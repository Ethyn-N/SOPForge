package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.dto.RelevanceChunkResponse;
import com.securedoc.securedoc_ai.dto.RelevancePreviewResponse;
import com.securedoc.securedoc_ai.dto.SopGenerateRequest;
import com.securedoc.securedoc_ai.dto.SopSourceChunkResponse;
import com.securedoc.securedoc_ai.dto.SopUpdateRequest;
import com.securedoc.securedoc_ai.exception.BadRequestException;
import com.securedoc.securedoc_ai.exception.NotFoundException;
import com.securedoc.securedoc_ai.model.Company;
import com.securedoc.securedoc_ai.model.CompanyRole;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.SopSourceChunk;
import com.securedoc.securedoc_ai.model.SopStatus;
import com.securedoc.securedoc_ai.model.SopVersion;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.SopRepository;
import com.securedoc.securedoc_ai.repository.SopSourceChunkRepository;
import com.securedoc.securedoc_ai.repository.SopVersionRepository;
import com.securedoc.securedoc_ai.service.ai.AiSopGenerator;
import com.securedoc.securedoc_ai.service.ai.GeneratedSopDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Service
public class SopService {

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final SopRepository sopRepository;
    private final SopSourceChunkRepository sopSourceChunkRepository;
    private final SopVersionRepository sopVersionRepository;
    private final AiSopGenerator aiSopGenerator;
    private final CompanyService companyService;

    public List<Sop> getSops(User user) {
        return sopRepository.findByOwner(user);
    }

    public List<Sop> getSops(Long companyId, User user) {
        Company company = companyService.getCompanyForUser(companyId, user);
        return sopRepository.findByCompany(company);
    }

    public Sop getSop(Long id, User user) {
        return sopRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new NotFoundException(
                        "sop with id " + id + " does not exist"
                ));
    }

    public Sop getSop(Long id, Long companyId, User user) {
        Company company = companyService.getCompanyForUser(companyId, user);
        return sopRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new NotFoundException(
                        "sop with id " + id + " does not exist"
                ));
    }

    public List<SopVersion> getSopVersions(Long sopId, User user) {
        Sop sop = getSop(sopId, user);
        return sopVersionRepository.findBySopOrderByVersionNumberAsc(sop);
    }

    public List<SopSourceChunkResponse> getSopSourceChunks(Long sopId, User user) {
        Sop sop = getSop(sopId, user);
        return sopSourceChunkRepository.findBySopOrderByDocumentChunkDocumentIdAscDocumentChunkChunkIndexAsc(sop)
                .stream()
                .map(SopSourceChunkResponse::new)
                .toList();
    }

    public SopVersion getSopVersion(Long sopId, Long versionId, User user) {
        Sop sop = getSop(sopId, user);

        return sopVersionRepository.findByIdAndSop(versionId, sop)
                .orElseThrow(() -> new NotFoundException(
                        "version with id " + versionId + " does not exist"
                ));
    }

    public List<Document> getGenerationDocuments(Long companyId, User user) {
        companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);

        return documentService.getDocuments(companyId, user)
                .stream()
                .filter(document -> document.getExtractionStatus() == ExtractionStatus.SUCCESS)
                .filter(document -> !isBlank(document.getExtractedText()))
                .sorted(Comparator.comparing(Document::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Document::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public RelevancePreviewResponse previewRelevance(SopGenerateRequest request, User user) {
        return previewRelevance(request, user, null);
    }

    public RelevancePreviewResponse previewRelevance(SopGenerateRequest request, User user, Long companyId) {
        if (request == null) {
            throw new BadRequestException("At least one source document is required.");
        }

        Company company = companyId == null
                ? null
                : companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);
        List<Document> documents = getSourceDocuments(request.sourceDocumentIds(), user, company);
        DocumentChunkService.RelevancePreview relevancePreview = documentChunkService.buildRelevancePreview(
                documents,
                request.title(),
                request.instructions()
        );

        return new RelevancePreviewResponse(
                relevancePreview.queryTerms(),
                relevancePreview.queryPhrases(),
                relevancePreview.chunks()
                        .stream()
                        .map(this::toRelevanceChunkResponse)
                        .toList()
        );
    }

    public Sop generateSop(SopGenerateRequest request, User user) {
        return generateSop(request, user, null);
    }

    public Sop generateSop(SopGenerateRequest request, User user, Long companyId) {
        if (request == null) {
            throw new BadRequestException("At least one source document is required.");
        }

        Company company = companyId == null
                ? null
                : companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);
        List<Document> documents = getSourceDocuments(request.sourceDocumentIds(), user, company);

        for (Document document : documents) {
            if (document.getExtractionStatus() != ExtractionStatus.SUCCESS || isBlank(document.getExtractedText())) {
                throw new BadRequestException("Document text must be successfully extracted before generating an SOP.");
            }
        }

        DocumentChunkService.RelevancePreview relevancePreview = documentChunkService.buildRelevancePreview(
                documents,
                request.title(),
                request.instructions()
        );

        List<Document> promptDocuments = documentChunkService.buildRelevantPromptDocuments(
                documents,
                request.title(),
                request.instructions()
        );

        GeneratedSopDraft generatedSopDraft = aiSopGenerator.generate(
                promptDocuments,
                request.title(),
                request.instructions(),
                user
        );

        Sop sop = new Sop(
                requiredGenerated(generatedSopDraft.title(), "title"),
                requiredGenerated(generatedSopDraft.purpose(), "purpose"),
                requiredGenerated(generatedSopDraft.scope(), "scope"),
                requiredGenerated(generatedSopDraft.procedure(), "procedure"),
                requiredGenerated(generatedSopDraft.roles(), "roles"),
                documents,
                user,
                company
        );
        sop.setSourceChunks(sourceChunksFor(sop, relevancePreview));

        Sop savedSop = sopRepository.save(sop);
        createVersion(savedSop, user, "Generated SOP");

        return savedSop;
    }

    private List<SopSourceChunk> sourceChunksFor(
            Sop sop,
            DocumentChunkService.RelevancePreview relevancePreview
    ) {
        return relevancePreview.chunks()
                .stream()
                .filter(relevanceChunk -> relevanceChunk.chunk().getId() != null)
                .map(relevanceChunk -> new SopSourceChunk(
                        sop,
                        relevanceChunk.chunk(),
                        relevanceChunk.score(),
                        relevanceChunk.matchedTerms()
                ))
                .toList();
    }

    private RelevanceChunkResponse toRelevanceChunkResponse(DocumentChunkService.RelevanceChunk relevanceChunk) {
        return new RelevanceChunkResponse(
                relevanceChunk.document().getId(),
                relevanceChunk.document().getOriginalFileName(),
                relevanceChunk.chunk().getId(),
                relevanceChunk.chunk().getChunkIndex(),
                relevanceChunk.score(),
                relevanceChunk.baseScore(),
                relevanceChunk.phraseScore(),
                relevanceChunk.score(),
                relevanceChunk.matchedTerms(),
                relevanceChunk.matchedPhrases(),
                preview(relevanceChunk.chunk().getContent())
        );
    }

    private String preview(String content) {
        if (content == null) {
            return "";
        }

        String compactContent = content.replaceAll("\\s+", " ").trim();

        if (compactContent.length() <= 300) {
            return compactContent;
        }

        return compactContent.substring(0, 300) + "...";
    }

    private List<Document> getSourceDocuments(List<Long> sourceDocumentIds, User user, Company company) {
        if (sourceDocumentIds == null || sourceDocumentIds.isEmpty()) {
            throw new BadRequestException("At least one source document is required.");
        }

        return sourceDocumentIds.stream()
                .distinct()
                .map(documentId -> getSourceDocument(documentId, user, company))
                .toList();
    }

    private Document getSourceDocument(Long documentId, User user, Company company) {
        if (company == null) {
            return documentService.getDocument(documentId, user);
        }

        return documentService.getDocument(documentId, company.getId(), user);
    }

    public Sop updateSop(Long id, SopUpdateRequest request, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED), "edited");
        applyUpdates(sop, request);

        Sop savedSop = sopRepository.save(sop);
        createVersion(savedSop, user, "Edited SOP");

        return savedSop;
    }

    public Sop updateSop(Long id, Long companyId, SopUpdateRequest request, User user) {
        companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);
        Sop sop = getSop(id, companyId, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED), "edited");
        applyUpdates(sop, request);

        Sop savedSop = sopRepository.save(sop);
        createVersion(savedSop, user, "Edited SOP");

        return savedSop;
    }

    private void applyUpdates(Sop sop, SopUpdateRequest request) {
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
    }

    public void deleteSop(Long id, User user) {
        Sop sop = getSop(id, user);
        sopRepository.delete(sop);
    }

    public void deleteSop(Long id, Long companyId, User user) {
        companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);
        Sop sop = getSop(id, companyId, user);
        sopRepository.delete(sop);
    }

    public Sop submitSopForReview(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED), "submitted for review");

        return updateStatus(sop, SopStatus.PENDING_REVIEW, user, "Submitted for review");
    }

    public Sop submitSopForReview(Long id, Long companyId, User user) {
        companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN, CompanyRole.REVIEWER);
        Sop sop = getSop(id, companyId, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED), "submitted for review");

        return updateStatus(sop, SopStatus.PENDING_REVIEW, user, "Submitted for review");
    }

    public Sop approveSop(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.PENDING_REVIEW), "approved");

        return updateStatus(sop, SopStatus.APPROVED, user, "Approved SOP");
    }

    public Sop approveSop(Long id, Long companyId, User user) {
        companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN, CompanyRole.APPROVER);
        Sop sop = getSop(id, companyId, user);
        requireStatus(sop, List.of(SopStatus.PENDING_REVIEW), "approved");

        return updateStatus(sop, SopStatus.APPROVED, user, "Approved SOP");
    }

    public Sop rejectSop(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.PENDING_REVIEW), "rejected");

        return updateStatus(sop, SopStatus.REJECTED, user, "Rejected SOP");
    }

    public Sop rejectSop(Long id, Long companyId, User user) {
        companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN, CompanyRole.REVIEWER);
        Sop sop = getSop(id, companyId, user);
        requireStatus(sop, List.of(SopStatus.PENDING_REVIEW), "rejected");

        return updateStatus(sop, SopStatus.REJECTED, user, "Rejected SOP");
    }

    public Sop archiveSop(Long id, User user) {
        Sop sop = getSop(id, user);
        requireStatus(sop, List.of(SopStatus.DRAFT, SopStatus.REJECTED, SopStatus.APPROVED), "archived");

        return updateStatus(sop, SopStatus.ARCHIVED, user, "Archived SOP");
    }

    public Sop archiveSop(Long id, Long companyId, User user) {
        companyService.requireCompanyRole(companyId, user, CompanyRole.OWNER, CompanyRole.ADMIN);
        Sop sop = getSop(id, companyId, user);
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
            throw new BadRequestException(
                    "SOP with status " + sop.getStatus() + " cannot be " + action + "."
            );
        }
    }

    private String requiredGenerated(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BadRequestException("AI SOP generation did not return " + fieldName + ".");
        }

        return value;
    }

    private String requiredUpdate(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BadRequestException(fieldName + " must not be blank.");
        }

        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
