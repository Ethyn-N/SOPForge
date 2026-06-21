package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.DocumentResponse;
import com.securedoc.securedoc_ai.dto.RelevancePreviewResponse;
import com.securedoc.securedoc_ai.dto.SopGenerateRequest;
import com.securedoc.securedoc_ai.dto.SopResponse;
import com.securedoc.securedoc_ai.dto.SopSourceChunkResponse;
import com.securedoc.securedoc_ai.dto.SopUpdateRequest;
import com.securedoc.securedoc_ai.dto.SopVersionResponse;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopVersion;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.service.SopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class SopController {

    private final SopService sopService;

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

    @GetMapping("/sops/{id}/source-chunks")
    public List<SopSourceChunkResponse> getSopSourceChunks(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        return sopService.getSopSourceChunks(id, user);
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

    @DeleteMapping("/sops/{id}")
    public ResponseEntity<Void> deleteSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        sopService.deleteSop(id, user);
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

    @PostMapping("/sops/{id}/approve")
    public SopResponse approveSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.approveSop(id, user);
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

    @PostMapping("/sops/{id}/archive")
    public SopResponse archiveSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.archiveSop(id, user);
        return new SopResponse(sop);
    }
}
