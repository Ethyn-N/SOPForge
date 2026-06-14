package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.RegisterRequest;
import com.securedoc.securedoc_ai.dto.UserResponse;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse registerUser(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalStateException("email is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalStateException("password is required");
        }

        boolean emailTaken = userRepository.findByEmail(request.getEmail()).isPresent();

        if (emailTaken) {
            throw new IllegalStateException("email already taken");
        }

        User user = new User(request.getEmail(), request.getPassword());
        User savedUser = userRepository.save(user);

        return new UserResponse(savedUser);
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::new)
                .toList();
    }

    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "user with id " + id + " does not exist"
                ));

        return new UserResponse(user);
    }
}