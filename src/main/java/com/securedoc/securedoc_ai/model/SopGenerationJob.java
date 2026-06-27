package com.securedoc.securedoc_ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "sop_generation_jobs")
public class SopGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String requestedTitle;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String roles;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sop_generation_job_documents",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    private List<Document> sourceDocuments = new ArrayList<>();

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SopGenerationJobStatus status;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_sop_id")
    private Sop resultSop;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Setter
    private LocalDateTime startedAt;

    @Setter
    private LocalDateTime completedAt;

    public SopGenerationJob(
            Company company,
            User owner,
            String requestedTitle,
            String instructions,
            String roles,
            List<Document> sourceDocuments
    ) {
        this.company = company;
        this.owner = owner;
        this.requestedTitle = requestedTitle;
        this.instructions = instructions;
        this.roles = roles;
        this.sourceDocuments = new ArrayList<>(sourceDocuments);
        this.status = SopGenerationJobStatus.QUEUED;
        this.createdAt = LocalDateTime.now();
    }
}
