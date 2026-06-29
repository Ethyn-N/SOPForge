package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.exception.BadRequestException;
import com.securedoc.securedoc_ai.exception.NotFoundException;
import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.CompanyRole;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import com.securedoc.securedoc_ai.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RequiredArgsConstructor
@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024;
    private static final int MAX_BULK_DOCUMENTS = 20;
    private static final long MAX_BULK_ARCHIVE_BYTES = 250L * 1024 * 1024;

    private static final Map<String, String> ALLOWED_FILE_TYPES = Map.of(
            "application/pdf", ".pdf",
            "text/plain", ".txt",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentChunkService documentChunkService;
    private final CompanyService companyService;

    public List<Document> getDocuments(User user) {
        return documentRepository.findByOwner(user);
    }

    public List<Document> getDocuments(Long companyId, User user) {
        Company company = companyService.getCompanyForUser(companyId, user);
        return documentRepository.findByCompany(company);
    }

    public Document getDocument(Long id, User user) {
        return documentRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new NotFoundException(
                        "document with id " + id + " does not exist"
                ));
    }

    public Document getDocument(Long id, Long companyId, User user) {
        Company company = companyService.getCompanyForUser(companyId, user);
        return documentRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new NotFoundException(
                        "document with id " + id + " does not exist"
                ));
    }

    public byte[] getStoredFile(Document document) {
        return fileStorageService.load(document.getStoredFileName());
    }

    public byte[] createDocumentArchive(List<Long> documentIds, Long companyId, User user) {
        List<Document> documents = getBulkDocuments(documentIds, companyId, user, false);
        long totalSize = documents.stream().mapToLong(Document::getFileSize).sum();

        if (totalSize > MAX_BULK_ARCHIVE_BYTES) {
            throw new BadRequestException("Selected documents must total 250 MB or less.");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            Set<String> entryNames = new HashSet<>();

            for (Document document : documents) {
                zip.putNextEntry(new ZipEntry(uniqueArchiveName(document.getOriginalFileName(), entryNames)));
                zip.write(getStoredFile(document));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BadRequestException("Document archive could not be created.");
        }
    }

    @Transactional
    public void deleteDocuments(List<Long> documentIds, Long companyId, User user) {
        List<Document> documents = getBulkDocuments(documentIds, companyId, user, true);

        for (Document document : documents) {
            deleteStoredFile(document);
            documentChunkService.deleteChunks(document);
        }
        documentRepository.deleteAll(documents);
    }

    @Transactional
    public Document uploadDocument(MultipartFile file, User user) {
        return uploadDocument(file, user, null);
    }

    @Transactional
    public Document uploadDocument(MultipartFile file, User user, Long companyId) {
        validateUpload(file);
        Company company = companyId == null
                ? null
                : companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);

        String contentType = file.getContentType();
        String originalFileName = cleanOriginalFileName(Objects.requireNonNull(file.getOriginalFilename()));
        String storedFileName = UUID.randomUUID() + getStoredExtension(originalFileName, contentType);
        byte[] fileBytes;

        try {
            fileBytes = file.getBytes();
            fileStorageService.store(storedFileName, new java.io.ByteArrayInputStream(fileBytes));
        } catch (IOException exception) {
            throw new BadRequestException("File upload could not be completed.");
        }

        Document document = new Document(
                originalFileName,
                storedFileName,
                contentType,
                file.getSize(),
                fileStorageService.storageUrl(storedFileName)
        );
        document.setOwner(user);
        document.setCompany(company);
        document.setUploadedAt(LocalDateTime.now());
        extractText(document, fileBytes);

        Document savedDocument = documentRepository.save(document);
        documentChunkService.createChunks(savedDocument);

        return savedDocument;
    }

    @Transactional
    public void deleteDocument(Long id, User user) {
        Document document = documentRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new NotFoundException(
                        "document with id " + id + " does not exist"
                ));

        deleteStoredFile(document);
        documentChunkService.deleteChunks(document);
        documentRepository.delete(document);
    }

    @Transactional
    public void deleteDocument(Long id, Long companyId, User user) {
        Company company = companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);
        Document document = documentRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new NotFoundException(
                        "document with id " + id + " does not exist"
                ));

        deleteStoredFile(document);
        documentChunkService.deleteChunks(document);
        documentRepository.delete(document);
    }

    private void deleteStoredFile(Document document) {
        fileStorageService.delete(document.getStoredFileName());
    }

    private List<Document> getBulkDocuments(
            List<Long> documentIds,
            Long companyId,
            User user,
            boolean requireManagement
    ) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BadRequestException("Select at least one document.");
        }

        List<Long> uniqueIds = documentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty() || uniqueIds.size() > MAX_BULK_DOCUMENTS) {
            throw new BadRequestException("Select between 1 and 20 documents.");
        }

        Company company = requireManagement
                ? companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN)
                : companyService.getCompanyForUser(companyId, user);
        Map<Long, Document> documentsById = documentRepository.findAllByIdInAndCompany(uniqueIds, company)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Document::getId, document -> document));

        if (documentsById.size() != uniqueIds.size()) {
            throw new NotFoundException("One or more selected documents do not exist.");
        }

        return uniqueIds.stream().map(documentsById::get).toList();
    }

    private String uniqueArchiveName(String originalFileName, Set<String> usedNames) {
        if (usedNames.add(originalFileName)) {
            return originalFileName;
        }

        int extensionIndex = originalFileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? originalFileName.substring(0, extensionIndex) : originalFileName;
        String extension = extensionIndex > 0 ? originalFileName.substring(extensionIndex) : "";
        int copyNumber = 1;
        String candidate;

        do {
            candidate = baseName + " (" + copyNumber++ + ")" + extension;
        } while (!usedNames.add(candidate));

        return candidate;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty.");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new BadRequestException("Uploaded file must have a file name.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Uploaded file must be 25 MB or smaller.");
        }

        if (!ALLOWED_FILE_TYPES.containsKey(file.getContentType())) {
            throw new BadRequestException("Unsupported file type.");
        }

        String originalFileName = cleanOriginalFileName(file.getOriginalFilename());
        String extension = getOriginalExtension(originalFileName);
        String expectedExtension = ALLOWED_FILE_TYPES.get(file.getContentType());

        if (extension != null && !extension.equals(expectedExtension)) {
            throw new BadRequestException("Unsupported file type.");
        }
    }

    private String getStoredExtension(String originalFileName, String contentType) {
        String extension = getOriginalExtension(originalFileName);

        if (extension != null) {
            return extension;
        }

        return ALLOWED_FILE_TYPES.get(contentType);
    }

    private String getOriginalExtension(String originalFileName) {
        int extensionStart = originalFileName.lastIndexOf('.');

        if (extensionStart >= 0 && extensionStart < originalFileName.length() - 1) {
            return originalFileName.substring(extensionStart).toLowerCase(Locale.ROOT);
        }

        return null;
    }

    private String cleanOriginalFileName(String originalFileName) {
        String normalizedFileName = originalFileName.replace('\\', '/');
        int fileNameStart = normalizedFileName.lastIndexOf('/');

        if (fileNameStart >= 0) {
            return normalizedFileName.substring(fileNameStart + 1);
        }

        return normalizedFileName;
    }

    private void extractText(Document document, byte[] fileBytes) {
        document.setExtractionStatus(ExtractionStatus.PENDING);

        try {
            String extractedText = switch (document.getFileType()) {
                case "text/plain" -> new String(fileBytes, StandardCharsets.UTF_8);
                case "application/pdf" -> extractPdfText(fileBytes);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        extractDocxText(fileBytes);
                default -> throw new BadRequestException("Unsupported file type.");
            };

            document.setExtractedText(extractedText);
            document.setExtractionError(null);
            document.setTextExtractedAt(LocalDateTime.now());
            document.setExtractionStatus(ExtractionStatus.SUCCESS);
        } catch (Exception exception) {
            document.setExtractedText(null);
            document.setExtractionError(getExtractionErrorMessage(exception));
            document.setTextExtractedAt(LocalDateTime.now());
            document.setExtractionStatus(ExtractionStatus.FAILED);
        }
    }

    private String getExtractionErrorMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }

    private String extractPdfText(byte[] fileBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            StringBuilder extractedText = new StringBuilder();

            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                textStripper.setStartPage(pageNumber);
                textStripper.setEndPage(pageNumber);
                String pageText = textStripper.getText(document).trim();

                if (!pageText.isBlank()) {
                    if (!extractedText.isEmpty()) {
                        extractedText.append("\n\n");
                    }
                    extractedText.append("[Page ")
                            .append(pageNumber)
                            .append("]\n")
                            .append(pageText);
                }
            }

            return extractedText.toString();
        }
    }

    private String extractDocxText(byte[] fileBytes) throws IOException {
        try (
                InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes);
                XWPFDocument document = new XWPFDocument(inputStream);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)
        ) {
            return extractor.getText();
        }
    }
}
