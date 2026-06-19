package com.securedoc.securedoc_ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "sop_versions")
public class SopVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sop_id", nullable = false)
    private Sop sop;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Column(name = "procedure_text", columnDefinition = "TEXT")
    private String procedure;

    @Column(columnDefinition = "TEXT")
    private String roles;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SopStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private String changeReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public SopVersion(Sop sop, Integer versionNumber, User createdBy, String changeReason) {
        this.sop = sop;
        this.versionNumber = versionNumber;
        this.title = sop.getTitle();
        this.purpose = sop.getPurpose();
        this.scope = sop.getScope();
        this.procedure = sop.getProcedure();
        this.roles = sop.getRoles();
        this.status = sop.getStatus();
        this.createdBy = createdBy;
        this.changeReason = changeReason;
        this.createdAt = LocalDateTime.now();
    }
}
