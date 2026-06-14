package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.service.DocumentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<Document> getDocuments() {
        return documentService.getDocuments();
    }

    @GetMapping("/{id}")
    public Document getDocument(@PathVariable Long id) {
        return documentService.getDocument(id);
    }

    @PostMapping
    public Document addDocument(@RequestBody Document document) {
        return documentService.addDocument(document);
    }

    @DeleteMapping("/{id}")
    public void deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
    }
}