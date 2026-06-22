package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.AuthResponse;
import com.securedoc.securedoc_ai.dto.EmailCheckRequest;
import com.securedoc.securedoc_ai.dto.EmailCheckResponse;
import com.securedoc.securedoc_ai.dto.LoginRequest;
import com.securedoc.securedoc_ai.dto.RegisterRequest;
import com.securedoc.securedoc_ai.dto.UserResponse;
import com.securedoc.securedoc_ai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public UserResponse registerUser(@RequestBody RegisterRequest request) {
        return userService.registerUser(request);
    }

    @PostMapping("/email-check")
    public EmailCheckResponse checkEmail(@RequestBody EmailCheckRequest request) {
        return userService.checkEmail(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return userService.login(request);
    }
}
