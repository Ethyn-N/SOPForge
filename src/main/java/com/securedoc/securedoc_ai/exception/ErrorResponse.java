package com.securedoc.securedoc_ai.exception;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ErrorResponse {

    private final String message;
    private final LocalDateTime timestamp;

    public ErrorResponse(String message) {
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}