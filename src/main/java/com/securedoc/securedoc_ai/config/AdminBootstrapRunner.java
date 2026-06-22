package com.securedoc.securedoc_ai.config;

import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.model.UserRole;
import com.securedoc.securedoc_ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties adminBootstrapProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!adminBootstrapProperties.isEnabled()) {
            return;
        }

        String email = normalizeEmail(adminBootstrapProperties.getEmail());
        String password = required(adminBootstrapProperties.getPassword(), "Admin password");

        Optional<User> existingAdmin = userRepository.findByEmail(email);
        User admin = existingAdmin
                .orElseGet(() -> new User(email, ""));

        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);

        if (existingAdmin.isEmpty() || adminBootstrapProperties.isResetPassword()) {
            admin.setPassword(passwordEncoder.encode(password));
        }

        userRepository.save(admin);
        LOGGER.info("Admin bootstrap completed for {}", email);
    }

    private String normalizeEmail(String value) {
        return required(value, "Admin email").toLowerCase();
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must be configured when admin bootstrap is enabled.");
        }

        return value.trim();
    }
}
