package com.securedoc.securedoc_ai.dto;

import lombok.Getter;

@Getter
public class EmailCheckResponse {

    private final String email;
    private final boolean registered;

    public EmailCheckResponse(String email, boolean registered) {
        this.email = email;
        this.registered = registered;
    }
}
