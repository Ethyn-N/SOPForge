package com.securedoc.securedoc_ai.dto;

public record SopUpdateRequest(
        String title,
        String purpose,
        String scope,
        String procedure,
        String roles
) {
}
