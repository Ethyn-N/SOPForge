package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.model.UserRole;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserResponse {

    private final Long id;
    private final String email;
    private final UserRole role;
    private final Boolean enabled;
    private final LocalDateTime createdAt;

    public UserResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.enabled = user.getEnabled();
        this.createdAt = user.getCreatedAt();
    }
}
