package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.UserRole;
import lombok.Getter;

@Getter
public class AuthResponse {

    private final String token;
    private final Long id;
    private final String name;
    private final String email;
    private final UserRole role;
    private final String message;

    public AuthResponse(String token, Long id, String name, String email, UserRole role, String message) {
        this.token = token;
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.message = message;
    }
}
