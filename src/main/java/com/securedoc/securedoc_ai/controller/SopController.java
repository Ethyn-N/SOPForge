package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.SopResponse;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.service.SopService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents/{documentId}/sops")
public class SopController {

    private final SopService sopService;

    @PostMapping("/generate")
    public SopResponse generateSop(
            @PathVariable Long documentId,
            @AuthenticationPrincipal User user
    ) {
        Sop sop = sopService.generateSop(documentId, user);
        return new SopResponse(sop);
    }
}
