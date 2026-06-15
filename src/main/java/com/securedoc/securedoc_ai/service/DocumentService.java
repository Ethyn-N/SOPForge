package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public List<Document> getDocuments(User user) {
        return documentRepository.findByOwner(user);
    }

    public Document getDocument(Long id, User user) {
        return documentRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalStateException(
                        "document with id " + id + " does not exist"
                ));
    }

    public Document addDocument(Document document, User user) {
        document.setOwner(user);

        if (document.getUploadedAt() == null) {
            document.setUploadedAt(LocalDateTime.now());
        }

        return documentRepository.save(document);
    }

    public void deleteDocument(Long id, User user) {
        Document document = documentRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalStateException(
                        "document with id " + id + " does not exist"
                ));

        documentRepository.delete(document);
    }
}