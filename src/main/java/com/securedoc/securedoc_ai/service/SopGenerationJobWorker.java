package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.SopGenerateRequest;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopGenerationJob;
import com.securedoc.securedoc_ai.model.SopGenerationJobStatus;
import com.securedoc.securedoc_ai.repository.SopGenerationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SopGenerationJobWorker {

    private final SopGenerationJobRepository jobRepository;
    private final SopService sopService;

    @Async("sopGenerationExecutor")
    public void process(Long jobId) {
        SopGenerationJob job = jobRepository.findById(jobId).orElse(null);

        if (job == null || job.getStatus() != SopGenerationJobStatus.QUEUED) {
            return;
        }

        job.setStatus(SopGenerationJobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            Sop sop = sopService.generateSop(
                    new SopGenerateRequest(
                            job.getRequestedTitle(),
                            job.getSourceDocuments().stream().map(document -> document.getId()).toList(),
                            job.getInstructions(),
                            job.getRoles()
                    ),
                    job.getOwner(),
                    job.getCompany().getId()
            );
            SopGenerationJob completedJob = jobRepository.findById(jobId).orElseThrow();
            completedJob.setStatus(SopGenerationJobStatus.SUCCESS);
            completedJob.setResultSop(sop);
            completedJob.setErrorMessage(null);
            completedJob.setCompletedAt(LocalDateTime.now());
            jobRepository.save(completedJob);
        } catch (Exception exception) {
            SopGenerationJob failedJob = jobRepository.findById(jobId).orElse(null);

            if (failedJob != null) {
                failedJob.setStatus(SopGenerationJobStatus.FAILED);
                failedJob.setErrorMessage(errorMessage(exception));
                failedJob.setCompletedAt(LocalDateTime.now());
                jobRepository.save(failedJob);
            }
        }
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "SOP generation failed."
                : message;
    }
}
