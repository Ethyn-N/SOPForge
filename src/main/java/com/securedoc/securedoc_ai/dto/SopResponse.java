package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class SopResponse {

    private final Long id;
    private final String title;
    private final String purpose;
    private final String scope;
    private final String procedure;
    private final String roles;
    private final SopStatus status;
    private final List<Long> sourceDocumentIds;
    private final List<String> sourceDocumentOriginalFileNames;
    private final Long ownerId;
    private final String ownerEmail;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public SopResponse(Sop sop) {
        this.id = sop.getId();
        this.title = sop.getTitle();
        this.purpose = sop.getPurpose();
        this.scope = sop.getScope();
        this.procedure = sop.getProcedure();
        this.roles = sop.getRoles();
        this.status = sop.getStatus();
        this.sourceDocumentIds = sop.getSourceDocuments()
                .stream()
                .map(Document::getId)
                .toList();
        this.sourceDocumentOriginalFileNames = sop.getSourceDocuments()
                .stream()
                .map(Document::getOriginalFileName)
                .toList();
        this.ownerId = sop.getOwner().getId();
        this.ownerEmail = sop.getOwner().getEmail();
        this.createdAt = sop.getCreatedAt();
        this.updatedAt = sop.getUpdatedAt();
    }
}
