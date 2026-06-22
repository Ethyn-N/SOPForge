package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:securedoc-auth-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "securedoc.storage.upload-dir=target/test-uploads/auth"
})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void registerRejectsEmailWithoutTopLevelDomain() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "asdf@h",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Registration failed. Please check your information and try again."
                ));
    }

    @Test
    void emailCheckReturnsWhetherEmailIsRegistered() throws Exception {
        userRepository.save(new User("known@example.com", "password"));

        mockMvc.perform(post("/api/auth/email-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "KNOWN@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("known@example.com"))
                .andExpect(jsonPath("$.registered").value(true));

        mockMvc.perform(post("/api/auth/email-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.registered").value(false));
    }

    @Test
    void emailCheckRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/email-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "asdf@h"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid email address."));
    }
}
