package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.SopResponse;
import com.securedoc.securedoc_ai.dto.SopUpdateRequest;
import com.securedoc.securedoc_ai.model.Sop;
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

    @GetMapping("/sops/{id}")
    public SopResponse getSop(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.getSop(id, user);
        return new SopResponse(sop);
    }

    @PostMapping("/documents/{documentId}/sops/generate")
    public SopResponse generateSop(
            @PathVariable Long documentId,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.generateSop(documentId, user);
        return new SopResponse(sop);
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
