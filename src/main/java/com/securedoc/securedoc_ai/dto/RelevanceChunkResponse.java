package com.securedoc.securedoc_ai.dto;

import java.util.List;

public record RelevanceChunkResponse(
        Long documentId,
        String originalFileName,
        Long chunkId,
        Integer chunkIndex,
        Integer score,
        List<String> matchedTerms,
        String contentPreview
) {
}
