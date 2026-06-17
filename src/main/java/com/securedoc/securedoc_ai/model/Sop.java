package com.securedoc.securedoc_ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "sops")
public class Sop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String scope;

    @Setter
    @Column(name = "procedure_text", columnDefinition = "TEXT")
    private String procedure;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String roles;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id", nullable = false)
    private Document sourceDocument;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SopStatus status = SopStatus.DRAFT;

    @Setter
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Sop(
            String title,
            String purpose,
            String scope,
            String procedure,
            String roles,
            Document sourceDocument,
            User owner
    ) {
        this.title = title;
        this.purpose = purpose;
        this.scope = scope;
        this.procedure = procedure;
        this.roles = roles;
        this.sourceDocument = sourceDocument;
        this.owner = owner;
        this.status = SopStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
