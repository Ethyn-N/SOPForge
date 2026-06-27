package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.DocumentResponse;
import com.securedoc.securedoc_ai.dto.RelevancePreviewResponse;
import com.securedoc.securedoc_ai.dto.SopGenerateRequest;
import com.securedoc.securedoc_ai.dto.SopGenerationJobResponse;
import com.securedoc.securedoc_ai.dto.SopResponse;
import com.securedoc.securedoc_ai.dto.SopSourceChunkResponse;
import com.securedoc.securedoc_ai.dto.SopUpdateRequest;
import com.securedoc.securedoc_ai.dto.SopVersionResponse;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopVersion;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.service.SopService;
import com.securedoc.securedoc_ai.service.SopGenerationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SopController {

    private final SopService sopService;
    private final SopGenerationJobService sopGenerationJobService;

    @GetMapping("/sops")
    public List<SopResponse> getSops(@AuthenticationPrincipal User user) {
        return sopService.getSops(user)
                .stream()
                .map(SopResponse::new)
                .toList();
    }

    @GetMapping("/companies/{companyId}/sops")
    public List<SopResponse> getCompanySops(
            @PathVariable Long companyId,
            @AuthenticationPrincipal User user
    ) {
        return sopService.getSops(companyId, user)
                .stream()
                .map(SopResponse::new)
                .toList();
    }

    @GetMapping("/companies/{companyId}/sops/generation-documents")
    public List<DocumentResponse> getCompanySopGenerationDocuments(
            @PathVariable Long companyId,
            @AuthenticationPrincipal User user
    ) {
        return sopService.getGenerationDocuments(companyId, user)
                .stream()
                .map(DocumentResponse::new)
                .toList();
    }

    @GetMapping("/sops/{id}")
    public SopResponse getSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.getSop(id, user);
        return new SopResponse(sop);
    }

    @GetMapping("/companies/{companyId}/sops/{id}")
    public SopResponse getCompanySop(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.getSop(id, companyId, user);
        return new SopResponse(sop);
    }

    @GetMapping("/sops/{id}/versions")
    public List<SopVersionResponse> getSopVersions(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return sopService.getSopVersions(id, user)
                .stream()
                .map(SopVersionResponse::new)
                .toList();
    }

    @GetMapping("/companies/{companyId}/sops/{id}/versions")
    public List<SopVersionResponse> getCompanySopVersions(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return sopService.getSopVersions(id, companyId, user)
                .stream()
                .map(SopVersionResponse::new)
                .toList();
    }

    @GetMapping("/sops/{id}/source-chunks")
    public List<SopSourceChunkResponse> getSopSourceChunks(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return sopService.getSopSourceChunks(id, user);
    }

    @GetMapping("/companies/{companyId}/sops/{id}/source-chunks")
    public List<SopSourceChunkResponse> getCompanySopSourceChunks(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return sopService.getSopSourceChunks(id, companyId, user);
    }

    @GetMapping("/sops/{id}/versions/{versionId}")
    public SopVersionResponse getSopVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @AuthenticationPrincipal User user
    ) {
        SopVersion sopVersion = sopService.getSopVersion(id, versionId, user);
        return new SopVersionResponse(sopVersion);
    }

    @GetMapping("/companies/{companyId}/sops/{id}/versions/{versionId}")
    public SopVersionResponse getCompanySopVersion(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @PathVariable Long versionId,
            @AuthenticationPrincipal User user
    ) {
        SopVersion sopVersion = sopService.getSopVersion(id, versionId, companyId, user);
        return new SopVersionResponse(sopVersion);
    }

    @PostMapping("/sops/generate")
    public SopResponse generateSopFromDocuments(
            @RequestBody SopGenerateRequest request,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.generateSop(request, user);
        return new SopResponse(sop);
    }

    @PostMapping("/companies/{companyId}/sops/generate")
    public SopResponse generateCompanySopFromDocuments(
            @PathVariable Long companyId,
            @RequestBody SopGenerateRequest request,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.generateSop(request, user, companyId);
        return new SopResponse(sop);
    }

    @PostMapping("/companies/{companyId}/sop-generation-jobs")
    public ResponseEntity<SopGenerationJobResponse> createCompanySopGenerationJob(
            @PathVariable Long companyId,
            @RequestBody SopGenerateRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SopGenerationJobResponse(
                        sopGenerationJobService.createJob(companyId, request, user)
                ));
    }

    @GetMapping("/companies/{companyId}/sop-generation-jobs")
    public List<SopGenerationJobResponse> getCompanySopGenerationJobs(
            @PathVariable Long companyId,
            @AuthenticationPrincipal User user
    ) {
        return sopGenerationJobService.getJobs(companyId, user)
                .stream()
                .map(SopGenerationJobResponse::new)
                .toList();
    }

    @GetMapping("/companies/{companyId}/sop-generation-jobs/{jobId}")
    public SopGenerationJobResponse getCompanySopGenerationJob(
            @PathVariable Long companyId,
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return new SopGenerationJobResponse(
                sopGenerationJobService.getJob(companyId, jobId, user)
        );
    }

    @PostMapping("/sops/relevance-preview")
    public RelevancePreviewResponse previewRelevance(
            @RequestBody SopGenerateRequest request,
            @AuthenticationPrincipal User user
    ) {
        return sopService.previewRelevance(request, user);
    }

    @PostMapping("/companies/{companyId}/sops/relevance-preview")
    public RelevancePreviewResponse previewCompanyRelevance(
            @PathVariable Long companyId,
            @RequestBody SopGenerateRequest request,
            @AuthenticationPrincipal User user
    ) {
        return sopService.previewRelevance(request, user, companyId);
    }

    @PatchMapping("/sops/{id}")
    public SopResponse updateSop(
            @PathVariable Long id,
            @RequestBody SopUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.updateSop(id, request, user);
        return new SopResponse(sop);
    }

    @PatchMapping("/companies/{companyId}/sops/{id}")
    public SopResponse updateCompanySop(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @RequestBody SopUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.updateSop(id, companyId, request, user);
        return new SopResponse(sop);
    }

    @DeleteMapping("/sops/{id}")
    public ResponseEntity<Void> deleteSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        sopService.deleteSop(id, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/companies/{companyId}/sops/{id}")
    public ResponseEntity<Void> deleteCompanySop(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        sopService.deleteSop(id, companyId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sops/{id}/submit")
    public SopResponse submitSopForReview(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.submitSopForReview(id, user);
        return new SopResponse(sop);
    }

    @PostMapping("/companies/{companyId}/sops/{id}/submit")
    public SopResponse submitCompanySopForReview(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.submitSopForReview(id, companyId, user);
        return new SopResponse(sop);
    }

    @PostMapping("/sops/{id}/approve")
    public SopResponse approveSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.approveSop(id, user);
        return new SopResponse(sop);
    }

    @PostMapping("/companies/{companyId}/sops/{id}/approve")
    public SopResponse approveCompanySop(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.approveSop(id, companyId, user);
        return new SopResponse(sop);
    }

    @PostMapping("/sops/{id}/reject")
    public SopResponse rejectSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.rejectSop(id, user);
        return new SopResponse(sop);
    }

    @PostMapping("/companies/{companyId}/sops/{id}/reject")
    public SopResponse rejectCompanySop(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.rejectSop(id, companyId, user);
        return new SopResponse(sop);
    }

    @PostMapping("/sops/{id}/archive")
    public SopResponse archiveSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.archiveSop(id, user);
        return new SopResponse(sop);
    }

    @PostMapping("/companies/{companyId}/sops/{id}/archive")
    public SopResponse archiveCompanySop(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.archiveSop(id, companyId, user);
        return new SopResponse(sop);
    }
}
