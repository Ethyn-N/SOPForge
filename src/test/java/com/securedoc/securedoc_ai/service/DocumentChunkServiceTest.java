package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.DocumentChunk;
import com.securedoc.securedoc_ai.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

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
        DocumentEmbeddingService embeddingService = mock(DocumentEmbeddingService.class);
        when(repository.findByDocumentOrderByChunkIndexAsc(document))
                .thenReturn(List.of(operationalChunk, safetyChunk, unrelatedChunk));
        when(embeddingService.semanticScores(
                List.of(operationalChunk, safetyChunk, unrelatedChunk),
                "Closing process Focus on sanitation"
        )).thenReturn(java.util.Map.of());

        DocumentChunkService service = new DocumentChunkService(repository, embeddingService);
        DocumentChunkService.RelevancePreview preview = service.buildRelevancePreview(
                List.of(document),
                "Closing process",
                "Focus on sanitation"
        );

        assertTrue(preview.chunks().stream().anyMatch(chunk -> chunk.chunk() == operationalChunk));
        assertTrue(preview.chunks().stream().anyMatch(chunk -> chunk.chunk() == safetyChunk));
    }

    @Test
    void semanticSimilarityOutweighsAWeakKeywordMatch() {
        Document document = new Document(
                "operations.pdf",
                "stored.pdf",
                "application/pdf",
                100L,
                "/uploads/stored.pdf"
        );
        DocumentChunk keywordChunk = new DocumentChunk(
                document,
                0,
                "Equipment inspection overview."
        );
        DocumentChunk semanticChunk = new DocumentChunk(
                document,
                1,
                "Confirm machinery is isolated and cannot restart before maintenance begins."
        );
        ReflectionTestUtils.setField(keywordChunk, "id", 10L);
        ReflectionTestUtils.setField(semanticChunk, "id", 11L);

        DocumentChunkRepository repository = mock(DocumentChunkRepository.class);
        DocumentEmbeddingService embeddingService = mock(DocumentEmbeddingService.class);
        List<DocumentChunk> chunks = List.of(keywordChunk, semanticChunk);
        when(repository.findByDocumentOrderByChunkIndexAsc(document)).thenReturn(chunks);
        when(embeddingService.semanticScores(chunks, "Equipment inspection"))
                .thenReturn(Map.of(10L, 0.10, 11L, 0.95));

        DocumentChunkService service = new DocumentChunkService(repository, embeddingService);
        DocumentChunkService.RelevancePreview preview = service.buildRelevancePreview(
                List.of(document),
                "Equipment inspection",
                null
        );

        int keywordScore = preview.chunks().stream()
                .filter(chunk -> chunk.chunk() == keywordChunk)
                .findFirst()
                .orElseThrow()
                .score();
        int semanticScore = preview.chunks().stream()
                .filter(chunk -> chunk.chunk() == semanticChunk)
                .findFirst()
                .orElseThrow()
                .score();
        assertTrue(semanticScore > keywordScore);
    }
}
