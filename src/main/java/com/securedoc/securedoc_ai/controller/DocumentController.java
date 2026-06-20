package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.DocumentChunkResponse;
import com.securedoc.securedoc_ai.dto.DocumentResponse;
import com.securedoc.securedoc_ai.dto.DocumentTextResponse;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.service.DocumentChunkService;
import com.securedoc.securedoc_ai.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;

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

    @GetMapping("/{id}/text")
    public DocumentTextResponse getDocumentText(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Document document = documentService.getDocument(id, user);
        return new DocumentTextResponse(document);
    }

    @GetMapping("/{id}/chunks")
    public List<DocumentChunkResponse> getDocumentChunks(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        Document document = documentService.getDocument(id, user);
        return documentChunkService.getChunks(document)
                .stream()
                .map(DocumentChunkResponse::new)
                .toList();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) throws IOException {
        Document document = documentService.getDocument(id, user);
        Path storedFilePath = documentService.getStoredFilePath(document);
        byte[] fileBytes = Files.readAllBytes(storedFilePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .contentLength(fileBytes.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getOriginalFileName())
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(fileBytes);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        Document savedDocument = documentService.uploadDocument(file, user);
        return new DocumentResponse(savedDocument);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        documentService.deleteDocument(id, user);
        return ResponseEntity.noContent().build();
    }
}
