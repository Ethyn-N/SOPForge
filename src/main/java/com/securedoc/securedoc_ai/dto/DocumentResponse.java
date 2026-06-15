package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DocumentResponse {

    private final Long id;
    private final String originalFileName;
    private final String storedFileName;
    private final String fileType;
    private final Long fileSize;
    private final String storageUrl;
    private final LocalDateTime uploadedAt;
    private final String extractedText;
    private final LocalDateTime textExtractedAt;
    private final ExtractionStatus extractionStatus;
    private final Long ownerId;
    private final String ownerEmail;

    public DocumentResponse(Document document) {
        this.id = document.getId();
        this.originalFileName = document.getOriginalFileName();
        this.storedFileName = document.getStoredFileName();
        this.fileType = document.getFileType();
        this.fileSize = document.getFileSize();
        this.storageUrl = document.getStorageUrl();
        this.uploadedAt = document.getUploadedAt();
        this.extractedText = document.getExtractedText();
        this.textExtractedAt = document.getTextExtractedAt();
        this.extractionStatus = document.getExtractionStatus();
        this.ownerId = document.getOwner().getId();
        this.ownerEmail = document.getOwner().getEmail();
    }
}
