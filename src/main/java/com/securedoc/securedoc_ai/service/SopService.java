package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.model.Sop;
import com.securedoc.securedoc_ai.model.User;
import com.securedoc.securedoc_ai.repository.SopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SopService {

    private static final int SOURCE_TEXT_PREVIEW_LENGTH = 2_000;

    private final DocumentService documentService;
    private final SopRepository sopRepository;

    public Sop generateSop(Long documentId, User user) {
        Document document = documentService.getDocument(documentId, user);

        if (document.getExtractionStatus() != ExtractionStatus.SUCCESS || isBlank(document.getExtractedText())) {
            throw new IllegalStateException("Document text must be successfully extracted before generating an SOP.");
        }

        Sop sop = new Sop(
                "SOP for " + document.getOriginalFileName(),
                "Provide a standard operating procedure based on the uploaded document.",
                "This SOP applies to the process information extracted from " + document.getOriginalFileName() + ".",
                buildProcedure(document.getExtractedText()),
                "Owner: " + user.getEmail() + "\nReviewer: TBD\nApprover: TBD",
                document,
                user
        );

        return sopRepository.save(sop);
    }

    private String buildProcedure(String extractedText) {
        return """
                AI generation placeholder.

                Source text preview:
                %s
                """.formatted(preview(extractedText));
    }

    private String preview(String extractedText) {
        if (extractedText.length() <= SOURCE_TEXT_PREVIEW_LENGTH) {
            return extractedText;
        }

        return extractedText.substring(0, SOURCE_TEXT_PREVIEW_LENGTH) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
