package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.SopRepository;
import com.securedoc.securedoc_ai.service.ai.AiSopGenerator;
import com.securedoc.securedoc_ai.service.ai.GeneratedSopDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SopService {

    private final DocumentService documentService;
    private final SopRepository sopRepository;
    private final AiSopGenerator aiSopGenerator;

    public List<Sop> getSops(User user) {
        return sopRepository.findByOwner(user);
    }

    public Sop getSop(Long id, User user) {
        return sopRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new IllegalStateException(
                        "sop with id " + id + " does not exist"
                ));
    }

    public Sop generateSop(Long documentId, User user) {
        Document document = documentService.getDocument(documentId, user);

        if (document.getExtractionStatus() != ExtractionStatus.SUCCESS || isBlank(document.getExtractedText())) {
            throw new IllegalStateException("Document text must be successfully extracted before generating an SOP.");
        }

        GeneratedSopDraft generatedSopDraft = aiSopGenerator.generate(document, user);

        Sop sop = new Sop(
                required(generatedSopDraft.title(), "title"),
                required(generatedSopDraft.purpose(), "purpose"),
                required(generatedSopDraft.scope(), "scope"),
                required(generatedSopDraft.procedure(), "procedure"),
                required(generatedSopDraft.roles(), "roles"),
                document,
                user
        );

        return sopRepository.save(sop);
    }

    private String required(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalStateException("AI SOP generation did not return " + fieldName + ".");
        }

        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
