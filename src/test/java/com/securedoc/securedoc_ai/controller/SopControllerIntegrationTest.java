package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.config.StorageProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopStatus;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import com.securedoc.securedoc_ai.repository.SopRepository;
import com.securedoc.securedoc_ai.repository.SopVersionRepository;
import com.securedoc.securedoc_ai.repository.UserRepository;
import com.securedoc.securedoc_ai.service.JwtService;
import com.securedoc.securedoc_ai.service.ai.AiSopGenerator;
import com.securedoc.securedoc_ai.service.ai.GeneratedSopDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private SopVersionRepository sopVersionRepository;

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
        sopVersionRepository.deleteAll();
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
                .andExpect(jsonPath("$.sourceDocumentId").doesNotExist())
                .andExpect(jsonPath("$.sourceDocumentOriginalFileName").doesNotExist())
                .andExpect(jsonPath("$.sourceDocumentIds", hasSize(1)))
                .andExpect(jsonPath("$.sourceDocumentIds[0]").value(document.getId()))
                .andExpect(jsonPath("$.sourceDocumentOriginalFileNames[0]").value("policy.txt"))
                .andExpect(jsonPath("$.ownerId").value(userOne.getId()))
                .andExpect(jsonPath("$.ownerEmail").value(userOne.getEmail()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        assertEquals(1, sopRepository.count());

        Sop savedSop = sopRepository.findByOwner(userOne).getFirst();
        assertEquals(SopStatus.DRAFT, savedSop.getStatus());
        assertEquals(document.getId(), savedSop.getSourceDocuments().getFirst().getId());
        assertEquals(userOne.getId(), savedSop.getOwner().getId());
        assertTrue(savedSop.getProcedure().contains("Step two"));
        assertEquals(1, sopVersionRepository.countBySop(savedSop));
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

    @Test
    void generateSopFromMultipleDocumentsCreatesDraftSopAndTracksSources() throws Exception {
        uploadTextDocument("server.txt", "Server duty text", userOneToken);
        uploadTextDocument("sanitation.txt", "Sanitation policy text", userOneToken);
        Document serverDocument = findDocumentByOriginalFileName(userOne, "server.txt");
        Document sanitationDocument = findDocumentByOriginalFileName(userOne, "sanitation.txt");

        mockMvc.perform(post("/api/sops/generate")
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Restaurant Service SOP",
                                  "sourceDocumentIds": [%d, %d],
                                  "instructions": "Focus on server duties and sanitation."
                                }
                                """.formatted(serverDocument.getId(), sanitationDocument.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Restaurant Service SOP"))
                .andExpect(jsonPath("$.procedure").value(containsString("Server duty text")))
                .andExpect(jsonPath("$.procedure").value(containsString("Sanitation policy text")))
                .andExpect(jsonPath("$.sourceDocumentId").doesNotExist())
                .andExpect(jsonPath("$.sourceDocumentIds", hasSize(2)))
                .andExpect(jsonPath("$.sourceDocumentIds[0]").value(serverDocument.getId()))
                .andExpect(jsonPath("$.sourceDocumentIds[1]").value(sanitationDocument.getId()))
                .andExpect(jsonPath("$.sourceDocumentOriginalFileNames[0]").value("server.txt"))
                .andExpect(jsonPath("$.sourceDocumentOriginalFileNames[1]").value("sanitation.txt"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        Sop savedSop = sopRepository.findByOwner(userOne).getFirst();
        assertEquals(1, sopRepository.count());
        assertEquals(1, sopVersionRepository.countBySop(savedSop));
    }

    @Test
    void generateSopFromMultipleDocumentsRejectsOtherUsersDocument() throws Exception {
        uploadTextDocument("own.txt", "Own text", userOneToken);
        uploadTextDocument("private.txt", "Private text", userTwoToken);
        Document ownDocument = findDocumentByOriginalFileName(userOne, "own.txt");
        Document otherUsersDocument = findDocumentByOriginalFileName(userTwo, "private.txt");

        mockMvc.perform(post("/api/sops/generate")
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Unauthorized Multi SOP",
                                  "sourceDocumentIds": [%d, %d]
                                }
                                """.formatted(ownDocument.getId(), otherUsersDocument.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "document with id " + otherUsersDocument.getId() + " does not exist"
                ));

        assertEquals(0, sopRepository.count());
    }

    @Test
    void generateSopFromMultipleDocumentsRejectsEmptySourceList() throws Exception {
        mockMvc.perform(post("/api/sops/generate")
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Empty SOP",
                                  "sourceDocumentIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one source document is required."));

        assertEquals(0, sopRepository.count());
    }

    @Test
    void getSopsOnlyReturnsAuthenticatedUsersSops() throws Exception {
        Sop ownSop = saveSopFor(userOne, "User One SOP");
        saveSopFor(userTwo, "User Two SOP");

        mockMvc.perform(get("/api/sops")
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownSop.getId()))
                .andExpect(jsonPath("$[0].title").value("User One SOP"))
                .andExpect(jsonPath("$[0].ownerId").value(userOne.getId()))
                .andExpect(jsonPath("$[0].ownerEmail").value(userOne.getEmail()))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void getSopAllowsOwner() throws Exception {
        Sop ownSop = saveSopFor(userOne, "Owner SOP");

        mockMvc.perform(get("/api/sops/{id}", ownSop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownSop.getId()))
                .andExpect(jsonPath("$.title").value("Owner SOP"))
                .andExpect(jsonPath("$.purpose").value("Test purpose."))
                .andExpect(jsonPath("$.scope").value("Test scope."))
                .andExpect(jsonPath("$.procedure").value("1. Test procedure."))
                .andExpect(jsonPath("$.roles").value("Server"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceDocumentIds[0]").value(ownSop.getSourceDocuments().getFirst().getId()))
                .andExpect(jsonPath("$.ownerId").value(userOne.getId()));
    }

    @Test
    void getSopRejectsSopOwnedByAnotherUser() throws Exception {
        Sop otherUsersSop = saveSopFor(userTwo, "Private SOP");

        mockMvc.perform(get("/api/sops/{id}", otherUsersSop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "sop with id " + otherUsersSop.getId() + " does not exist"
                ));
    }

    @Test
    void updateSopEditsProvidedFieldsAndKeepsOmittedFields() throws Exception {
        Sop sop = saveSopFor(userOne, "Original SOP");

        mockMvc.perform(patch("/api/sops/{id}", sop.getId())
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated SOP",
                                  "procedure": "1. Updated step."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sop.getId()))
                .andExpect(jsonPath("$.title").value("Updated SOP"))
                .andExpect(jsonPath("$.purpose").value("Test purpose."))
                .andExpect(jsonPath("$.scope").value("Test scope."))
                .andExpect(jsonPath("$.procedure").value("1. Updated step."))
                .andExpect(jsonPath("$.roles").value("Server"));

        Sop updatedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals("Updated SOP", updatedSop.getTitle());
        assertEquals("Test purpose.", updatedSop.getPurpose());
        assertEquals("1. Updated step.", updatedSop.getProcedure());
        assertEquals(1, sopVersionRepository.countBySop(updatedSop));
    }

    @Test
    void updateSopRejectsBlankFields() throws Exception {
        Sop sop = saveSopFor(userOne, "Original SOP");

        mockMvc.perform(patch("/api/sops/{id}", sop.getId())
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title must not be blank."));

        Sop unchangedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals("Original SOP", unchangedSop.getTitle());
    }

    @Test
    void updateSopRejectsSopOwnedByAnotherUser() throws Exception {
        Sop otherUsersSop = saveSopFor(userTwo, "Private SOP");

        mockMvc.perform(patch("/api/sops/{id}", otherUsersSop.getId())
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Unauthorized Update"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "sop with id " + otherUsersSop.getId() + " does not exist"
                ));

        Sop unchangedSop = sopRepository.findById(otherUsersSop.getId()).orElseThrow();
        assertEquals("Private SOP", unchangedSop.getTitle());
    }

    @Test
    void deleteSopRemovesOwnedSop() throws Exception {
        Sop sop = saveSopFor(userOne, "Delete Me SOP");

        mockMvc.perform(delete("/api/sops/{id}", sop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertTrue(sopRepository.findById(sop.getId()).isEmpty());
    }

    @Test
    void deleteSopRejectsSopOwnedByAnotherUser() throws Exception {
        Sop otherUsersSop = saveSopFor(userTwo, "Keep Me SOP");

        mockMvc.perform(delete("/api/sops/{id}", otherUsersSop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "sop with id " + otherUsersSop.getId() + " does not exist"
                ));

        assertTrue(sopRepository.findById(otherUsersSop.getId()).isPresent());
    }

    @Test
    void submitSopMovesDraftToPendingReview() throws Exception {
        Sop sop = saveSopFor(userOne, "Submit SOP");

        mockMvc.perform(post("/api/sops/{id}/submit", sop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        Sop updatedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals(SopStatus.PENDING_REVIEW, updatedSop.getStatus());
        assertEquals(1, sopVersionRepository.countBySop(updatedSop));
    }

    @Test
    void submitSopRejectsOtherUsersSop() throws Exception {
        Sop otherUsersSop = saveSopFor(userTwo, "Other Submit SOP");

        mockMvc.perform(post("/api/sops/{id}/submit", otherUsersSop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "sop with id " + otherUsersSop.getId() + " does not exist"
                ));
    }

    @Test
    void approveSopMovesPendingReviewToApproved() throws Exception {
        Sop sop = saveSopFor(userOne, "Approve SOP", SopStatus.PENDING_REVIEW);

        mockMvc.perform(post("/api/sops/{id}/approve", sop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Sop updatedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals(SopStatus.APPROVED, updatedSop.getStatus());
    }

    @Test
    void approveSopRejectsInvalidStatus() throws Exception {
        Sop sop = saveSopFor(userOne, "Draft Approval SOP");

        mockMvc.perform(post("/api/sops/{id}/approve", sop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SOP with status DRAFT cannot be approved."));

        Sop unchangedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals(SopStatus.DRAFT, unchangedSop.getStatus());
    }

    @Test
    void rejectSopMovesPendingReviewToRejected() throws Exception {
        Sop sop = saveSopFor(userOne, "Reject SOP", SopStatus.PENDING_REVIEW);

        mockMvc.perform(post("/api/sops/{id}/reject", sop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Sop updatedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals(SopStatus.REJECTED, updatedSop.getStatus());
    }

    @Test
    void archiveSopMovesApprovedToArchived() throws Exception {
        Sop sop = saveSopFor(userOne, "Archive SOP", SopStatus.APPROVED);

        mockMvc.perform(post("/api/sops/{id}/archive", sop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        Sop updatedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals(SopStatus.ARCHIVED, updatedSop.getStatus());
    }

    @Test
    void updateSopRejectsApprovedSop() throws Exception {
        Sop sop = saveSopFor(userOne, "Approved SOP", SopStatus.APPROVED);

        mockMvc.perform(patch("/api/sops/{id}", sop.getId())
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Changed Approved SOP"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SOP with status APPROVED cannot be edited."));

        Sop unchangedSop = sopRepository.findById(sop.getId()).orElseThrow();
        assertEquals("Approved SOP", unchangedSop.getTitle());
    }

    @Test
    void getSopVersionsReturnsSnapshotsInVersionOrder() throws Exception {
        uploadTextDocument("versioned-policy.txt", "Original text", userOneToken);
        Document document = documentRepository.findByOwner(userOne).getFirst();

        mockMvc.perform(post("/api/documents/{id}/sops/generate", document.getId())
                        .header("Authorization", bearer(userOneToken)));

        Sop sop = sopRepository.findByOwner(userOne).getFirst();

        mockMvc.perform(patch("/api/sops/{id}", sop.getId())
                        .header("Authorization", bearer(userOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Edited Versioned SOP"
                                }
                                """));

        mockMvc.perform(post("/api/sops/{id}/submit", sop.getId())
                        .header("Authorization", bearer(userOneToken)));

        mockMvc.perform(get("/api/sops/{id}/versions", sop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$[0].title").value("SOP for versioned-policy.txt"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].changeReason").value("Generated SOP"))
                .andExpect(jsonPath("$[1].versionNumber").value(2))
                .andExpect(jsonPath("$[1].title").value("Edited Versioned SOP"))
                .andExpect(jsonPath("$[1].status").value("DRAFT"))
                .andExpect(jsonPath("$[1].changeReason").value("Edited SOP"))
                .andExpect(jsonPath("$[2].versionNumber").value(3))
                .andExpect(jsonPath("$[2].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[2].changeReason").value("Submitted for review"));
    }

    @Test
    void getSopVersionAllowsOwner() throws Exception {
        uploadTextDocument("single-version.txt", "Version source", userOneToken);
        Document document = documentRepository.findByOwner(userOne).getFirst();

        mockMvc.perform(post("/api/documents/{id}/sops/generate", document.getId())
                        .header("Authorization", bearer(userOneToken)));

        Sop sop = sopRepository.findByOwner(userOne).getFirst();
        Long versionId = sopVersionRepository.findBySopOrderByVersionNumberAsc(sop).getFirst().getId();

        mockMvc.perform(get("/api/sops/{id}/versions/{versionId}", sop.getId(), versionId)
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId))
                .andExpect(jsonPath("$.sopId").value(sop.getId()))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.createdById").value(userOne.getId()))
                .andExpect(jsonPath("$.createdByEmail").value(userOne.getEmail()))
                .andExpect(jsonPath("$.changeReason").value("Generated SOP"));
    }

    @Test
    void getSopVersionsRejectsOtherUsersSop() throws Exception {
        Sop otherUsersSop = saveSopFor(userTwo, "Other User Version SOP");

        mockMvc.perform(get("/api/sops/{id}/versions", otherUsersSop.getId())
                        .header("Authorization", bearer(userOneToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "sop with id " + otherUsersSop.getId() + " does not exist"
                ));
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

    private Document findDocumentByOriginalFileName(User owner, String originalFileName) {
        return documentRepository.findByOwner(owner)
                .stream()
                .filter(document -> document.getOriginalFileName().equals(originalFileName))
                .findFirst()
                .orElseThrow();
    }

    private Sop saveSopFor(User owner, String title) {
        return saveSopFor(owner, title, SopStatus.DRAFT);
    }

    private Sop saveSopFor(User owner, String title, SopStatus status) {
        Document document = new Document(
                title + ".txt",
                "stored-" + title + ".txt",
                "text/plain",
                100L,
                "/uploads/stored-" + title + ".txt"
        );
        document.setOwner(owner);
        document.setExtractionStatus(ExtractionStatus.SUCCESS);
        document.setExtractedText("Source text for " + title);
        Document savedDocument = documentRepository.save(document);

        Sop sop = new Sop(
                title,
                "Test purpose.",
                "Test scope.",
                "1. Test procedure.",
                "Server",
                List.of(savedDocument),
                owner
        );
        sop.setStatus(status);

        return sopRepository.save(sop);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class TestAiConfiguration {

        @Bean
        @Primary
        AiSopGenerator testAiSopGenerator() {
            return (documents, requestedTitle, _, user) -> new GeneratedSopDraft(
                    requestedTitle == null || requestedTitle.isBlank()
                            ? "SOP for " + documents.getFirst().getOriginalFileName()
                            : requestedTitle,
                    "Test purpose generated from AI.",
                    "Test scope generated from AI.",
                    "Test procedure generated from: " + documents.stream()
                            .map(Document::getExtractedText)
                            .toList(),
                    "Owner: " + user.getEmail() + "\nReviewer: TBD\nApprover: TBD"
            );
        }
    }
}
