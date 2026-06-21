package com.securedoc.securedoc_ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sop_source_documents",
            joinColumns = @JoinColumn(name = "sop_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    private List<Document> sourceDocuments = new ArrayList<>();

    @OneToMany(mappedBy = "sop", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SopSourceChunk> sourceChunks = new LinkedHashSet<>();

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

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
            List<Document> sourceDocuments,
            User owner
    ) {
        this(title, purpose, scope, procedure, roles, sourceDocuments, owner, null);
    }

    public Sop(
            String title,
            String purpose,
            String scope,
            String procedure,
            String roles,
            List<Document> sourceDocuments,
            User owner,
            Company company
    ) {
        this.title = title;
        this.purpose = purpose;
        this.scope = scope;
        this.procedure = procedure;
        this.roles = roles;
        this.sourceDocuments = new ArrayList<>(sourceDocuments);
        this.owner = owner;
        this.company = company;
        this.status = SopStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void setSourceChunks(List<SopSourceChunk> sourceChunks) {
        this.sourceChunks = new LinkedHashSet<>(sourceChunks);
    }
}
