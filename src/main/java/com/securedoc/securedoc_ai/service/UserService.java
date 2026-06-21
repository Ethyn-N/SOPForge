package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.AuthResponse;
import com.securedoc.securedoc_ai.dto.LoginRequest;
import com.securedoc.securedoc_ai.dto.RegisterRequest;
import com.securedoc.securedoc_ai.dto.UserResponse;
import com.securedoc.securedoc_ai.exception.AuthException;
import com.securedoc.securedoc_ai.exception.BadRequestException;
import com.securedoc.securedoc_ai.exception.NotFoundException;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserService {

    private static final String REGISTRATION_FAILED_MESSAGE =
            "Registration failed. Please check your information and try again.";

    private static final String LOGIN_FAILED_MESSAGE =
            "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserResponse registerUser(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException(REGISTRATION_FAILED_MESSAGE);
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException(REGISTRATION_FAILED_MESSAGE);
        }

        boolean emailTaken = userRepository.findByEmail(request.getEmail()).isPresent();

        if (emailTaken) {
            throw new BadRequestException(REGISTRATION_FAILED_MESSAGE);
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getEmail().toLowerCase().trim(),
                hashedPassword
        );

        User savedUser = userRepository.save(user);

        return new UserResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            throw new AuthException(LOGIN_FAILED_MESSAGE);
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new AuthException(LOGIN_FAILED_MESSAGE));

        if (!user.getEnabled()) {
            throw new AuthException(LOGIN_FAILED_MESSAGE);
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        );

        if (!passwordMatches) {
            throw new AuthException(LOGIN_FAILED_MESSAGE);
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                "Login successful"
        );
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::new)
                .toList();
    }

    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "User request could not be completed."
                ));

        return new UserResponse(user);
    }
}
