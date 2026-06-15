package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.config.StorageProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.User;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:securedoc-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
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
                .andExpect(jsonPath("$.extractedText").value("policy content"))
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractionStatus").value("SUCCESS"))
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
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractedText", containsString("Employee handbook text")));
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
                .andExpect(jsonPath("$.textExtractedAt").exists())
                .andExpect(jsonPath("$.extractedText", containsString("DOCX handbook text")));
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
                .andExpect(jsonPath("$[0]", not(org.hamcrest.Matchers.hasKey("owner"))))
                .andExpect(jsonPath("$[0]", not(org.hamcrest.Matchers.hasKey("password"))));
    }

    @Test
    void getDocumentRejectsDocumentOwnedByAnotherUser() throws Exception {
        Document otherUsersDocument = saveDocumentFor(userTwo, "private.pdf");

        mockMvc.perform(get("/api/documents/{id}", otherUsersDocument.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
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
