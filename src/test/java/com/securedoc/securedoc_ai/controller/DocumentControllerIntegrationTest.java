package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import com.securedoc.securedoc_ai.repository.UserRepository;
import com.securedoc.securedoc_ai.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "spring.jpa.show-sql=false"
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

    private User userOne;
    private User userTwo;
    private String userOneToken;
    private String userTwoToken;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        userRepository.deleteAll();

        userOne = userRepository.save(new User("user-one@example.com", "password"));
        userTwo = userRepository.save(new User("user-two@example.com", "password"));
        userOneToken = jwtService.generateToken(userOne);
        userTwoToken = jwtService.generateToken(userTwo);
    }

    @Test
    void addDocumentReturnsSafeDocumentResponse() throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.originalFileName").value("policy.pdf"))
                .andExpect(jsonPath("$.storedFileName").value("abc-123.pdf"))
                .andExpect(jsonPath("$.fileType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").value(42))
                .andExpect(jsonPath("$.storageUrl").value("/uploads/abc-123.pdf"))
                .andExpect(jsonPath("$.uploadedAt").exists())
                .andExpect(jsonPath("$.ownerId").value(userOne.getId()))
                .andExpect(jsonPath("$.ownerEmail").value(userOne.getEmail()))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.enabled").doesNotExist())
                .andExpect(jsonPath("$.authorities").doesNotExist());
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
}
