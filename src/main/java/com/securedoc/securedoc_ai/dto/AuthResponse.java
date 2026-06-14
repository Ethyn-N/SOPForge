package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.UserRole;
import lombok.Getter;

@Getter
public class AuthResponse {

    private final Long id;
    private final String email;
    private final UserRole role;
    private final String message;

    public AuthResponse(Long id, String email, UserRole role, String message) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.message = message;
    }
}