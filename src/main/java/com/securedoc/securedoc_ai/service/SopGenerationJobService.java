package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.SopGenerateRequest;
import com.securedoc.securedoc_ai.exception.BadRequestException;
import com.securedoc.securedoc_ai.exception.NotFoundException;
import com.securedoc.securedoc_ai.model.*;
import com.securedoc.securedoc_ai.repository.SopGenerationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SopGenerationJobService {

    private final SopGenerationJobRepository jobRepository;
    private final CompanyService companyService;
    private final DocumentService documentService;
    private final SopGenerationJobWorker jobWorker;

    public SopGenerationJob createJob(Long companyId, SopGenerateRequest request, User user) {
        if (request == null || request.sourceDocumentIds() == null || request.sourceDocumentIds().isEmpty()) {
            throw new BadRequestException("At least one source document is required.");
        }

        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("title must not be blank.");
        }

        if (request.roles() == null || request.roles().isBlank()) {
            throw new BadRequestException("roles must not be blank.");
        }

        Company company = companyService.requireCompanyRole(
                companyId,
                user,
                CompanyRole.OWNER,
                CompanyRole.ADMIN
        );
        List<Document> documents = request.sourceDocumentIds().stream()
                .distinct()
                .map(documentId -> documentService.getDocument(documentId, companyId, user))
                .toList();

        for (Document document : documents) {
            if (document.getExtractionStatus() != ExtractionStatus.SUCCESS
                    || document.getExtractedText() == null
                    || document.getExtractedText().isBlank()) {
                throw new BadRequestException(
                        "Document text must be successfully extracted before generating an SOP."
                );
            }
        }

        SopGenerationJob job = jobRepository.save(new SopGenerationJob(
                company,
                user,
                request.title().trim(),
                request.instructions(),
                request.roles(),
                documents
        ));
        jobWorker.process(job.getId());
        return getJob(companyId, job.getId(), user);
    }

    public SopGenerationJob getJob(Long companyId, Long jobId, User user) {
        Company company = companyService.getCompanyForUser(companyId, user);
        return jobRepository.findByIdAndCompany(jobId, company)
                .orElseThrow(() -> new NotFoundException(
                        "SOP generation job with id " + jobId + " does not exist"
                ));
    }

    public List<SopGenerationJob> getJobs(Long companyId, User user) {
        Company company = companyService.getCompanyForUser(companyId, user);
        return jobRepository.findByCompanyOrderByCreatedAtDesc(company);
    }
}
