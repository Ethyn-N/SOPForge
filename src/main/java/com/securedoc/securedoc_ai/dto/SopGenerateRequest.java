package com.securedoc.securedoc_ai.dto;

import java.util.List;

public record SopGenerateRequest(
        String title,
        List<Long> sourceDocumentIds,
        String instructions,
        String roles
) {
}
