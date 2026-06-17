package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.config.StorageProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopStatus;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import com.securedoc.securedoc_ai.repository.SopRepository;
import com.securedoc.securedoc_ai.repository.UserRepository;
import com.securedoc.securedoc_ai.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "securedoc.storage.upload-dir=target/test-uploads/sops"
})
class SopControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private SopRepository sopRepository;

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
        sopRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();

        userOne = userRepository.save(new User("sop-user-one@example.com", "password"));
        userTwo = userRepository.save(new User("sop-user-two@example.com", "password"));
        userOneToken = jwtService.generateToken(userOne);
        userTwoToken = jwtService.generateToken(userTwo);
    }

    @Test
    void generateSopCreatesDraftSopFromExtractedDocumentText() throws Exception {
        uploadTextDocument("policy.txt", "Step one\nStep two", userOneToken);
        Document document = documentRepository.findByOwner(userOne).getFirst();

        mockMvc.perform(post("/api/documents/{id}/sops/generate", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("SOP for policy.txt"))
                .andExpect(jsonPath("$.purpose").isNotEmpty())
                .andExpect(jsonPath("$.scope").isNotEmpty())
                .andExpect(jsonPath("$.procedure").value(containsString("Step one")))
                .andExpect(jsonPath("$.roles").value(containsString(userOne.getEmail())))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceDocumentId").value(document.getId()))
                .andExpect(jsonPath("$.sourceDocumentOriginalFileName").value("policy.txt"))
                .andExpect(jsonPath("$.ownerId").value(userOne.getId()))
                .andExpect(jsonPath("$.ownerEmail").value(userOne.getEmail()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        assertEquals(1, sopRepository.count());

        Sop savedSop = sopRepository.findByOwner(userOne).getFirst();
        assertEquals(SopStatus.DRAFT, savedSop.getStatus());
        assertEquals(document.getId(), savedSop.getSourceDocument().getId());
        assertEquals(userOne.getId(), savedSop.getOwner().getId());
        assertTrue(savedSop.getProcedure().contains("Step two"));
    }

    @Test
    void generateSopRejectsDocumentOwnedByAnotherUser() throws Exception {
        uploadTextDocument("private.txt", "Private process", userTwoToken);
        Document document = documentRepository.findByOwner(userTwo).getFirst();

        mockMvc.perform(post("/api/documents/{id}/sops/generate", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "document with id " + document.getId() + " does not exist"
                ));

        assertEquals(0, sopRepository.count());
    }

    @Test
    void generateSopRejectsDocumentWithoutSuccessfulExtraction() throws Exception {
        Document document = saveFailedExtractionDocument();

        mockMvc.perform(post("/api/documents/{id}/sops/generate", document.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Document text must be successfully extracted before generating an SOP."
                ));

        assertEquals(0, sopRepository.count());
    }

    private void uploadTextDocument(String fileName, String content, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                .file(file)
                .header("Authorization", bearer(token)));
    }

    private Document saveFailedExtractionDocument() {
        Document document = new Document(
                "broken.pdf",
                "stored-broken.pdf",
                "application/pdf",
                100L,
                "/uploads/stored-broken.pdf"
        );
        document.setOwner(userOne);
        document.setExtractionStatus(ExtractionStatus.FAILED);
        document.setExtractionError("Could not parse PDF.");

        return documentRepository.save(document);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
