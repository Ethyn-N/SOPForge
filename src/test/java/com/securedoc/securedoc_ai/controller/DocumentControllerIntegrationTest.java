package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.config.StorageProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.DocumentChunkRepository;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import com.securedoc.securedoc_ai.repository.UserRepository;
import com.securedoc.securedoc_ai.service.JwtService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:securedoc-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/h2",
        "securedoc.ai.ollama.semantic-search-enabled=false",
        "securedoc.storage.upload-dir=target/test-uploads/documents"
})
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StorageProperties storageProperties;

    private User userOne;
    private User userTwo;
    private String userOneToken;
    private String userTwoToken;

    @BeforeEach
    void setUp() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of(storageProperties.getUploadDir()));
        documentChunkRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();

        userOne = userRepository.save(new User("user-one@example.com", "password"));
        userTwo = userRepository.save(new User("user-two@example.com", "password"));
        userOneToken = jwtService.generateToken(userOne);
        userTwoToken = jwtService.generateToken(userTwo);
    }

    @Test
    void uploadDocumentStoresFileAndReturnsSafeDocumentResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "policy content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.originalFileName").value("policy.txt"))
                .andExpect(jsonPath("$.storedFileName").exists())
                .andExpect(jsonPath("$.storedFileName").value(not("policy.txt")))
                .andExpect(jsonPath("$.fileType").value("text/plain"))
                .andExpect(jsonPath("$.fileSize").value(14))
                .andExpect(jsonPath("$.storageUrl").exists())
                .andExpect(jsonPath("$.uploadedAt").exists())
                .andExpect(jsonPath("$.extractedText").doesNotExist())
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.extractionError").doesNotExist())
                .andExpect(jsonPath("$.ownerId").value(userOne.getId()))
                .andExpect(jsonPath("$.ownerEmail").value(userOne.getEmail()))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.enabled").doesNotExist())
                .andExpect(jsonPath("$.authorities").doesNotExist());

        Document savedDocument = documentRepository.findByOwner(userOne).getFirst();
        Path storedFilePath = Path.of(storageProperties.getUploadDir()).resolve(savedDocument.getStoredFileName());

        assertNotEquals("policy.txt", savedDocument.getStoredFileName());
        assertTrue(Files.exists(storedFilePath));
        assertEquals("policy content", Files.readString(storedFilePath));
        assertEquals("/uploads/documents/" + savedDocument.getStoredFileName(), savedDocument.getStorageUrl());
        assertEquals("policy content", savedDocument.getExtractedText());
        assertEquals(ExtractionStatus.SUCCESS, savedDocument.getExtractionStatus());
        assertEquals(1, documentChunkRepository.findByDocumentOrderByChunkIndexAsc(savedDocument).size());
    }

    @Test
    void uploadDocumentExtractsPdfText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "handbook.pdf",
                "application/pdf",
                createPdf()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.extractionError").doesNotExist())
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    @Test
    void uploadDocumentExtractsDocxText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "handbook.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocx()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.extractionError").doesNotExist())
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    @Test
    void uploadDocumentStoresExtractionErrorWhenParsingFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.pdf",
                "application/pdf",
                "not really a pdf".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractionStatus").value("FAILED"))
                .andExpect(jsonPath("$.extractionError").isNotEmpty())
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    @Test
    void jsonDocumentCreationIsRejected() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalFileName": "policy.pdf",
                                  "storedFileName": "abc-123.pdf",
                                  "fileType": "application/pdf",
                                  "fileSize": 42,
                                  "storageUrl": "/uploads/abc-123.pdf"
                                }
                                """))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadDocumentRejectsUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/x-msdownload",
                "binary".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported file type."));
    }

    @Test
    void uploadDocumentRejectsMismatchedFileExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/pdf",
                "not really a pdf".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported file type."));
    }

    @Test
    void getDocumentsOnlyReturnsAuthenticatedUsersDocuments() throws Exception {
        Document ownDocument = saveDocumentFor(userOne, "user-one-plan.pdf");
        saveDocumentFor(userTwo, "user-two-plan.pdf");

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownDocument.getId()))
                .andExpect(jsonPath("$[0].originalFileName").value("user-one-plan.pdf"))
                .andExpect(jsonPath("$[0].ownerId").value(userOne.getId()))
                .andExpect(jsonPath("$[0].extractedText").doesNotExist())
                .andExpect(jsonPath("$[0]", not(org.hamcrest.Matchers.hasKey("owner"))))
                .andExpect(jsonPath("$[0]", not(org.hamcrest.Matchers.hasKey("password"))));
    }

    @Test
    void getDocumentTextReturnsExtractedTextForOwner() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "only load this when needed".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header("Authorization", bearer(userOneToken)));

        Document document = documentRepository.findByOwner(userOne).getFirst();

        mockMvc.perform(get("/api/documents/{id}/text", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(document.getId()))
                .andExpect(jsonPath("$.originalFileName").value("policy.txt"))
                .andExpect(jsonPath("$.extractedText").value("only load this when needed"))
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.extractionError").doesNotExist());
    }

    @Test
    void getDocumentTextRejectsDocumentOwnedByAnotherUser() throws Exception {
        Document otherUsersDocument = saveDocumentFor(userTwo, "private.pdf");

        mockMvc.perform(get("/api/documents/{id}/text", otherUsersDocument.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "document with id " + otherUsersDocument.getId() + " does not exist"
                ));
    }

    @Test
    void getDocumentChunksReturnsChunksForOwner() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "server duties and sanitation steps".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                .file(file)
                .header("Authorization", bearer(userOneToken)));

        Document document = documentRepository.findByOwner(userOne).getFirst();

        mockMvc.perform(get("/api/documents/{id}/chunks", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].documentId").value(document.getId()))
                .andExpect(jsonPath("$[0].chunkIndex").value(0))
                .andExpect(jsonPath("$[0].content").value("server duties and sanitation steps"));
    }

    @Test
    void getDocumentChunksRejectsDocumentOwnedByAnotherUser() throws Exception {
        Document otherUsersDocument = saveDocumentFor(userTwo, "private.pdf");

        mockMvc.perform(get("/api/documents/{id}/chunks", otherUsersDocument.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "document with id " + otherUsersDocument.getId() + " does not exist"
                ));
    }

    @Test
    void downloadDocumentReturnsStoredFileForOwner() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "download me".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                .file(file)
                .header("Authorization", bearer(userOneToken)));

        Document document = documentRepository.findByOwner(userOne).getFirst();

        mockMvc.perform(get("/api/documents/{id}/download", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().longValue("Content-Length", 11))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"policy.txt\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("download me"));
    }

    @Test
    void downloadDocumentRejectsDocumentOwnedByAnotherUser() throws Exception {
        Document otherUsersDocument = saveDocumentFor(userTwo, "private.pdf");

        mockMvc.perform(get("/api/documents/{id}/download", otherUsersDocument.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "document with id " + otherUsersDocument.getId() + " does not exist"
                ));
    }

    @Test
    void deleteDocumentRemovesStoredFileAndDatabaseRecordForOwner() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "delete me".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                .file(file)
                .header("Authorization", bearer(userOneToken)));

        Document document = documentRepository.findByOwner(userOne).getFirst();
        Path storedFilePath = Path.of(storageProperties.getUploadDir()).resolve(document.getStoredFileName());

        assertTrue(Files.exists(storedFilePath));

        mockMvc.perform(delete("/api/documents/{id}", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertTrue(Files.notExists(storedFilePath));
        assertTrue(documentRepository.findById(document.getId()).isEmpty());
    }

    @Test
    void deleteDocumentRejectsDocumentOwnedByAnotherUserAndKeepsStoredFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "private.txt",
                "text/plain",
                "keep me".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                .file(file)
                .header("Authorization", bearer(userTwoToken)));

        Document document = documentRepository.findByOwner(userTwo).getFirst();
        Path storedFilePath = Path.of(storageProperties.getUploadDir()).resolve(document.getStoredFileName());

        mockMvc.perform(delete("/api/documents/{id}", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "document with id " + document.getId() + " does not exist"
                ));

        assertTrue(Files.exists(storedFilePath));
        assertTrue(documentRepository.findById(document.getId()).isPresent());
    }

    @Test
    void getDocumentRejectsDocumentOwnedByAnotherUser() throws Exception {
        Document otherUsersDocument = saveDocumentFor(userTwo, "private.pdf");

        mockMvc.perform(get("/api/documents/{id}", otherUsersDocument.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "document with id " + otherUsersDocument.getId() + " does not exist"
                ));
    }

    @Test
    void getDocumentAllowsOwner() throws Exception {
        Document ownDocument = saveDocumentFor(userTwo, "owner-visible.pdf");

        mockMvc.perform(get("/api/documents/{id}", ownDocument.getId())
                        .header("Authorization", bearer(userTwoToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownDocument.getId()))
                .andExpect(jsonPath("$.originalFileName").value("owner-visible.pdf"))
                .andExpect(jsonPath("$.ownerId").value(userTwo.getId()))
                .andExpect(jsonPath("$.ownerEmail").value(userTwo.getEmail()))
                .andExpect(jsonPath("$.extractedText").doesNotExist())
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private Document saveDocumentFor(User owner, String originalFileName) {
        Document document = new Document(
                originalFileName,
                "stored-" + originalFileName,
                "application/pdf",
                100L,
                "/uploads/stored-" + originalFileName
        );
        document.setOwner(owner);
        return documentRepository.save(document);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] createPdf() throws IOException {
        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText("Employee handbook text");
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createDocx() throws IOException {
        try (
                XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("DOCX handbook text");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
