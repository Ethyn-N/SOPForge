package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.SopStatus;
import com.securedoc.securedoc_ai.model.SopVersion;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SopVersionResponse {

    private final Long id;
    private final Long sopId;
    private final Integer versionNumber;
    private final String title;
    private final String purpose;
    private final String scope;
    private final String procedure;
    private final String roles;
    private final SopStatus status;
    private final Long createdById;
    private final String createdByEmail;
    private final String changeReason;
    private final LocalDateTime createdAt;

    public SopVersionResponse(SopVersion sopVersion) {
        this.id = sopVersion.getId();
        this.sopId = sopVersion.getSop().getId();
        this.versionNumber = sopVersion.getVersionNumber();
        this.title = sopVersion.getTitle();
        this.purpose = sopVersion.getPurpose();
        this.scope = sopVersion.getScope();
        this.procedure = sopVersion.getProcedure();
        this.roles = sopVersion.getRoles();
        this.status = sopVersion.getStatus();
        this.createdById = sopVersion.getCreatedBy().getId();
        this.createdByEmail = sopVersion.getCreatedBy().getEmail();
        this.changeReason = sopVersion.getChangeReason();
        this.createdAt = sopVersion.getCreatedAt();
    }
}
