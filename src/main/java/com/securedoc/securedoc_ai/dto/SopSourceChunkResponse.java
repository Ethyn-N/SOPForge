package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.DocumentChunk;
import com.securedoc.securedoc_ai.model.SopSourceChunk;

import java.util.List;

public record SopSourceChunkResponse(
        Long id,
        Long documentId,
        String originalFileName,
        Long chunkId,
        Integer chunkIndex,
        Integer score,
        List<String> matchedTerms,
        String contentPreview
) {

    public SopSourceChunkResponse(SopSourceChunk sopSourceChunk) {
        this(
                sopSourceChunk.getId(),
                document(sopSourceChunk).getId(),
                document(sopSourceChunk).getOriginalFileName(),
                documentChunk(sopSourceChunk).getId(),
                documentChunk(sopSourceChunk).getChunkIndex(),
                sopSourceChunk.getScore(),
                sopSourceChunk.getMatchedTermsList(),
                preview(documentChunk(sopSourceChunk).getContent())
        );
    }

    private static DocumentChunk documentChunk(SopSourceChunk sopSourceChunk) {
        return sopSourceChunk.getDocumentChunk();
    }

    private static Document document(SopSourceChunk sopSourceChunk) {
        return documentChunk(sopSourceChunk).getDocument();
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }

        String compactContent = content.replaceAll("\\s+", " ").trim();

        if (compactContent.length() <= 300) {
            return compactContent;
        }

        return compactContent.substring(0, 300) + "...";
    }
}
