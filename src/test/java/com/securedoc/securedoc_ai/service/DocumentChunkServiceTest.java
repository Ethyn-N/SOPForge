package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.DocumentChunk;
import com.securedoc.securedoc_ai.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentChunkServiceTest {

    @Test
    void relevancePreviewIncludesControlEvidenceThatDoesNotMatchTheUserQuery() {
        Document document = new Document(
                "maintenance.pdf",
                "stored.pdf",
                "application/pdf",
                100L,
                "/uploads/stored.pdf"
        );
        DocumentChunk operationalChunk = new DocumentChunk(
                document,
                0,
                "Complete the closing sanitation checklist and clean the work area."
        );
        DocumentChunk safetyChunk = new DocumentChunk(
                document,
                1,
                "DANGER: Disconnect all power before servicing. Work must be completed only by qualified personnel."
        );
        DocumentChunk unrelatedChunk = new DocumentChunk(
                document,
                2,
                "Product history and corporate background."
        );
        DocumentChunkRepository repository = mock(DocumentChunkRepository.class);
        when(repository.findByDocumentOrderByChunkIndexAsc(document))
                .thenReturn(List.of(operationalChunk, safetyChunk, unrelatedChunk));

        DocumentChunkService service = new DocumentChunkService(repository);
        DocumentChunkService.RelevancePreview preview = service.buildRelevancePreview(
                List.of(document),
                "Closing process",
                "Focus on sanitation"
        );

        assertTrue(preview.chunks().stream().anyMatch(chunk -> chunk.chunk() == operationalChunk));
        assertTrue(preview.chunks().stream().anyMatch(chunk -> chunk.chunk() == safetyChunk));
    }
}
