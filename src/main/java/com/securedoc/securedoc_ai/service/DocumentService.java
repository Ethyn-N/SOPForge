package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public List<Document> getDocuments() {
        return documentRepository.findAll();
    }

    public Document addDocument(Document document) {
        if (document.getUploadedAt() == null) {
            document.setUploadedAt(java.time.LocalDateTime.now());
        }

        return documentRepository.save(document);
    }

    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "document with id " + id + " does not exist"
                ));
    }

    public void deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new IllegalStateException(
                    "document with id " + id + " does not exist"
            );
        }

        documentRepository.deleteById(id);
    }
}