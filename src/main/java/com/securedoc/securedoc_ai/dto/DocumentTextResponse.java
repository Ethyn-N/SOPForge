package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DocumentTextResponse {

    private final Long documentId;
    private final String originalFileName;
    private final String extractedText;
    private final LocalDateTime textExtractedAt;
    private final ExtractionStatus extractionStatus;

    public DocumentTextResponse(Document document) {
        this.documentId = document.getId();
        this.originalFileName = document.getOriginalFileName();
        this.extractedText = document.getExtractedText();
        this.textExtractedAt = document.getTextExtractedAt();
        this.extractionStatus = document.getExtractionStatus();
    }
}
