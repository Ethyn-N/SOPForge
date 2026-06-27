package com.securedoc.securedoc_ai.dto;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.SopGenerationJob;
import com.securedoc.securedoc_ai.model.SopGenerationJobStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record SopGenerationJobResponse(
        Long id,
        Long companyId,
        String requestedTitle,
        String instructions,
        String roles,
        SopGenerationJobStatus status,
        List<Long> sourceDocumentIds,
        List<String> sourceDocumentOriginalFileNames,
        Long resultSopId,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public SopGenerationJobResponse(SopGenerationJob job) {
        this(
                job.getId(),
                job.getCompany().getId(),
                job.getRequestedTitle(),
                job.getInstructions(),
                job.getRoles(),
                job.getStatus(),
                job.getSourceDocuments().stream()
                        .sorted(Comparator.comparing(Document::getId))
                        .map(Document::getId)
                        .toList(),
                job.getSourceDocuments().stream()
                        .sorted(Comparator.comparing(Document::getId))
                        .map(Document::getOriginalFileName)
                        .toList(),
                job.getResultSop() == null ? null : job.getResultSop().getId(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
