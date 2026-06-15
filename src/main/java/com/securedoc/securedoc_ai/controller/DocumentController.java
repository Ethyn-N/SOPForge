package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.DocumentResponse;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public List<DocumentResponse> getDocuments(@AuthenticationPrincipal User user) {
        return documentService.getDocuments(user)
                .stream()
                .map(DocumentResponse::new)
                .toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse getDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Document document = documentService.getDocument(id, user);
        return new DocumentResponse(document);
    }

    @PostMapping
    public DocumentResponse addDocument(
            @RequestBody Document document,
            @AuthenticationPrincipal User user
    ) {
        Document savedDocument = documentService.addDocument(document, user);
        return new DocumentResponse(savedDocument);
    }

    @DeleteMapping("/{id}")
    public void deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        documentService.deleteDocument(id, user);
    }
}