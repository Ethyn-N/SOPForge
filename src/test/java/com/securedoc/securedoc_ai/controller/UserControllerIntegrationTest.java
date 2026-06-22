package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.model.UserRole;
import com.securedoc.securedoc_ai.repository.UserRepository;
import com.securedoc.securedoc_ai.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:securedoc-users-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "securedoc.storage.upload-dir=target/test-uploads/users"
})
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private User regularUser;
    private String regularUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        regularUser = userRepository.save(new User("regular@example.com", "password"));

        User adminUser = new User("admin@example.com", "password");
        adminUser.setRole(UserRole.ADMIN);
        adminUser = userRepository.save(adminUser);

        regularUserToken = jwtService.generateToken(regularUser);
        adminToken = jwtService.generateToken(adminUser);
    }

    @Test
    void getUsersRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearer(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUsersAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void getUserByIdRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/users/{id}", regularUser.getId())
                        .header("Authorization", bearer(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserByIdAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/api/users/{id}", regularUser.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(regularUser.getId()))
                .andExpect(jsonPath("$.email").value("regular@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
