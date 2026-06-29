package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.AuthResponse;
import com.securedoc.securedoc_ai.dto.EmailCheckRequest;
import com.securedoc.securedoc_ai.dto.EmailCheckResponse;
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
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class UserService {

    private static final String REGISTRATION_FAILED_MESSAGE =
            "Registration failed. Please check your information and try again.";

    private static final String LOGIN_FAILED_MESSAGE =
            "Invalid email or password.";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserResponse registerUser(RegisterRequest request) {
        if (request == null
                || request.getName() == null
                || request.getName().isBlank()
                || request.getName().trim().length() > 101) {
            throw new BadRequestException(REGISTRATION_FAILED_MESSAGE);
        }
        if (isInvalidEmail(request.getEmail())) {
            throw new BadRequestException(REGISTRATION_FAILED_MESSAGE);
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException(REGISTRATION_FAILED_MESSAGE);
        }

        String normalizedEmail = normalizeEmail(request.getEmail());
        boolean emailTaken = userRepository.findByEmail(normalizedEmail).isPresent();

        if (emailTaken) {
            throw new BadRequestException(REGISTRATION_FAILED_MESSAGE);
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName().trim(),
                normalizedEmail,
                hashedPassword
        );

        User savedUser = userRepository.save(user);

        return new UserResponse(savedUser);
    }

    public EmailCheckResponse checkEmail(EmailCheckRequest request) {
        if (isInvalidEmail(request.getEmail())) {
            throw new BadRequestException("Enter a valid email address.");
        }

        String normalizedEmail = normalizeEmail(request.getEmail());
        boolean registered = userRepository.findByEmail(normalizedEmail).isPresent();

        return new EmailCheckResponse(normalizedEmail, registered);
    }

    public AuthResponse login(LoginRequest request) {
        if (isInvalidEmail(request.getEmail()) || request.getPassword() == null) {
            throw new AuthException(LOGIN_FAILED_MESSAGE);
        }

        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
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
                user.getName(),
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

    private boolean isInvalidEmail(String email) {
        return email == null || !EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }
}
