package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.SopUpdateRequest;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.SopRepository;
import com.securedoc.securedoc_ai.service.ai.AiSopGenerator;
import com.securedoc.securedoc_ai.service.ai.GeneratedSopDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
                requiredGenerated(generatedSopDraft.title(), "title"),
                requiredGenerated(generatedSopDraft.purpose(), "purpose"),
                requiredGenerated(generatedSopDraft.scope(), "scope"),
                requiredGenerated(generatedSopDraft.procedure(), "procedure"),
                requiredGenerated(generatedSopDraft.roles(), "roles"),
                document,
                user
        );

        return sopRepository.save(sop);
    }

    public Sop updateSop(Long id, SopUpdateRequest request, User user) {
        Sop sop = getSop(id, user);

        if (request.title() != null) {
            sop.setTitle(requiredUpdate(request.title(), "title"));
        }

        if (request.purpose() != null) {
            sop.setPurpose(requiredUpdate(request.purpose(), "purpose"));
        }

        if (request.scope() != null) {
            sop.setScope(requiredUpdate(request.scope(), "scope"));
        }

        if (request.procedure() != null) {
            sop.setProcedure(requiredUpdate(request.procedure(), "procedure"));
        }

        if (request.roles() != null) {
            sop.setRoles(requiredUpdate(request.roles(), "roles"));
        }

        sop.setUpdatedAt(LocalDateTime.now());

        return sopRepository.save(sop);
    }

    public void deleteSop(Long id, User user) {
        Sop sop = getSop(id, user);
        sopRepository.delete(sop);
    }

    private String requiredGenerated(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalStateException("AI SOP generation did not return " + fieldName + ".");
        }

        return value;
    }

    private String requiredUpdate(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalStateException(fieldName + " must not be blank.");
        }

        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
