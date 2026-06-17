package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.config.StorageProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@RequiredArgsConstructor
@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private static final Map<String, String> ALLOWED_FILE_TYPES = Map.of(
            "application/pdf", ".pdf",
            "text/plain", ".txt",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

    private final DocumentRepository documentRepository;
    private final StorageProperties storageProperties;

    public List<Document> getDocuments(User user) {
        return documentRepository.findByOwner(user);
    }

    public Document getDocument(Long id, User user) {
        return documentRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalStateException(
                        "document with id " + id + " does not exist"
                ));
    }

    public Path getStoredFilePath(Document document) {
        Path uploadPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path storedFilePath = uploadPath.resolve(document.getStoredFileName()).normalize();

        if (!storedFilePath.startsWith(uploadPath) || !Files.exists(storedFilePath)) {
            throw new IllegalStateException("Document file could not be found.");
        }

        return storedFilePath;
    }

    public Document uploadDocument(MultipartFile file, User user) {
        validateUpload(file);

        String contentType = file.getContentType();
        String originalFileName = cleanOriginalFileName(Objects.requireNonNull(file.getOriginalFilename()));
        String storedFileName = UUID.randomUUID() + getStoredExtension(originalFileName, contentType);
        Path uploadPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path storedFilePath = uploadPath.resolve(storedFileName).normalize();

        if (!storedFilePath.startsWith(uploadPath)) {
            throw new IllegalStateException("File upload could not be completed.");
        }

        try {
            Files.createDirectories(uploadPath);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, storedFilePath);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("File upload could not be completed.");
        }

        Document document = new Document(
                originalFileName,
                storedFileName,
                contentType,
                file.getSize(),
                "/uploads/documents/" + storedFileName
        );
        document.setOwner(user);
        document.setUploadedAt(LocalDateTime.now());
        extractText(document, storedFilePath);

        return documentRepository.save(document);
    }

    public void deleteDocument(Long id, User user) {
        Document document = documentRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalStateException(
                        "document with id " + id + " does not exist"
                ));

        deleteStoredFile(document);
        documentRepository.delete(document);
    }

    private void deleteStoredFile(Document document) {
        Path uploadPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path storedFilePath = uploadPath.resolve(document.getStoredFileName()).normalize();

        if (!storedFilePath.startsWith(uploadPath)) {
            throw new IllegalStateException("Document file could not be deleted.");
        }

        try {
            Files.deleteIfExists(storedFilePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Document file could not be deleted.");
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Uploaded file must not be empty.");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalStateException("Uploaded file must have a file name.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalStateException("Uploaded file must be 10 MB or smaller.");
        }

        if (!ALLOWED_FILE_TYPES.containsKey(file.getContentType())) {
            throw new IllegalStateException("Unsupported file type.");
        }

        String originalFileName = cleanOriginalFileName(file.getOriginalFilename());
        String extension = getOriginalExtension(originalFileName);
        String expectedExtension = ALLOWED_FILE_TYPES.get(file.getContentType());

        if (extension != null && !extension.equals(expectedExtension)) {
            throw new IllegalStateException("Unsupported file type.");
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

    private void extractText(Document document, Path storedFilePath) {
        document.setExtractionStatus(ExtractionStatus.PENDING);

        try {
            String extractedText = switch (document.getFileType()) {
                case "text/plain" -> Files.readString(storedFilePath, StandardCharsets.UTF_8);
                case "application/pdf" -> extractPdfText(storedFilePath);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        extractDocxText(storedFilePath);
                default -> throw new IllegalStateException("Unsupported file type.");
            };

            document.setExtractedText(extractedText);
            document.setTextExtractedAt(LocalDateTime.now());
            document.setExtractionStatus(ExtractionStatus.SUCCESS);
        } catch (Exception exception) {
            document.setExtractedText(null);
            document.setTextExtractedAt(LocalDateTime.now());
            document.setExtractionStatus(ExtractionStatus.FAILED);
        }
    }

    private String extractPdfText(Path storedFilePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(storedFilePath.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocxText(Path storedFilePath) throws IOException {
        try (
                InputStream inputStream = Files.newInputStream(storedFilePath);
                XWPFDocument document = new XWPFDocument(inputStream);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)
        ) {
            return extractor.getText();
        }
    }
}
