package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.SopUpdateRequest;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopStatus;
import com.securedoc.securedoc_ai.model.SopVersion;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.SopRepository;
import com.securedoc.securedoc_ai.repository.SopVersionRepository;
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
    private final SopVersionRepository sopVersionRepository;
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

    public List<SopVersion> getSopVersions(Long sopId, User user) {
        Sop sop = getSop(sopId, user);
        return sopVersionRepository.findBySopOrderByVersionNumberAsc(sop);
    }

    public SopVersion getSopVersion(Long sopId, Long versionId, User user) {
        Sop sop = getSop(sopId, user);

        return sopVersionRepository.findByIdAndSop(versionId, sop)
                .orElseThrow(() -> new IllegalStateException(
                        "version with id " + versionId + " does not exist"
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

        Sop savedSop = sopRepository.save(sop);
        createVersion(savedSop, user, "Generated SOP");

        return savedSop;
    }

    public Sop updateSop(Long id, SopUpdateRequest request, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED), "edited");

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

        Sop savedSop = sopRepository.save(sop);
        createVersion(savedSop, user, "Edited SOP");

        return savedSop;
    }

    public void deleteSop(Long id, User user) {
        Sop sop = getSop(id, user);
        sopRepository.delete(sop);
    }

    public Sop submitSopForReview(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED), "submitted for review");

        return updateStatus(sop, SopStatus.PENDING_REVIEW, user, "Submitted for review");
    }

    public Sop approveSop(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.PENDING_REVIEW), "approved");

        return updateStatus(sop, SopStatus.APPROVED, user, "Approved SOP");
    }

    public Sop rejectSop(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.PENDING_REVIEW), "rejected");

        return updateStatus(sop, SopStatus.REJECTED, user, "Rejected SOP");
    }

    public Sop archiveSop(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED, SopStatus.APPROVED), "archived");

        return updateStatus(sop, SopStatus.ARCHIVED, user, "Archived SOP");
    }

    private Sop updateStatus(Sop sop, SopStatus status, User user, String changeReason) {
        sop.setStatus(status);
        sop.setUpdatedAt(LocalDateTime.now());

        Sop savedSop = sopRepository.save(sop);
        createVersion(savedSop, user, changeReason);

        return savedSop;
    }

    private void createVersion(Sop sop, User user, String changeReason) {
        int versionNumber = sopVersionRepository.countBySop(sop) + 1;
        SopVersion sopVersion = new SopVersion(sop, versionNumber, user, changeReason);
        sopVersionRepository.save(sopVersion);
    }

    private void requireStatus(Sop sop, List<SopStatus> allowedStatuses, String action) {
        if (!allowedStatuses.contains(sop.getStatus())) {
            throw new IllegalStateException(
                    "SOP with status " + sop.getStatus() + " cannot be " + action + "."
            );
        }
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
