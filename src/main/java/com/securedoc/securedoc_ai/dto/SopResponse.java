package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopSourceChunk;
import com.securedoc.securedoc_ai.model.SopStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
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
    private final Integer sourceChunkCount;
    private final List<SopSourceChunkResponse> sourceChunks;
    private final Long ownerId;
    private final String ownerEmail;
    private final Long companyId;
    private final String companyName;
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
                .distinct()
                .sorted(Comparator.comparing(Document::getId))
                .map(Document::getId)
                .toList();
        this.sourceDocumentOriginalFileNames = sop.getSourceDocuments()
                .stream()
                .distinct()
                .sorted(Comparator.comparing(Document::getId))
                .map(Document::getOriginalFileName)
                .toList();
        this.sourceChunkCount = sop.getSourceChunks().size();
        this.sourceChunks = sop.getSourceChunks()
                .stream()
                .sorted(Comparator
                        .comparing((SopSourceChunk sourceChunk) ->
                                sourceChunk.getDocumentChunk().getDocument().getId())
                        .thenComparing(sourceChunk -> sourceChunk.getDocumentChunk().getChunkIndex()))
                .map(SopSourceChunkResponse::new)
                .toList();
        this.ownerId = sop.getOwner().getId();
        this.ownerEmail = sop.getOwner().getEmail();
        this.companyId = sop.getCompany() == null ? null : sop.getCompany().getId();
        this.companyName = sop.getCompany() == null ? null : sop.getCompany().getName();
        this.createdAt = sop.getCreatedAt();
        this.updatedAt = sop.getUpdatedAt();
    }
}
