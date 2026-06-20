package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.DocumentChunk;

public record DocumentChunkResponse(
        Long id,
        Long documentId,
        Integer chunkIndex,
        String content
) {

    public DocumentChunkResponse(DocumentChunk documentChunk) {
        this(
                documentChunk.getId(),
                documentChunk.getDocument().getId(),
                documentChunk.getChunkIndex(),
                documentChunk.getContent()
        );
    }
}
