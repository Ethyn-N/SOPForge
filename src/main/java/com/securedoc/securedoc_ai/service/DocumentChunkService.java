package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.DocumentChunk;
import com.securedoc.securedoc_ai.model.ExtractionStatus;
import com.securedoc.securedoc_ai.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class DocumentChunkService {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_CHUNKS_FOR_AI = 8;

    private final DocumentChunkRepository documentChunkRepository;

    @Transactional
    public void createChunks(Document document) {
        documentChunkRepository.deleteByDocument(document);

        if (document.getExtractionStatus() != ExtractionStatus.SUCCESS || isBlank(document.getExtractedText())) {
            return;
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> chunkContents = splitIntoChunks(document.getExtractedText());

        for (int index = 0; index < chunkContents.size(); index++) {
            chunks.add(new DocumentChunk(document, index, chunkContents.get(index)));
        }

        documentChunkRepository.saveAll(chunks);
    }

    @Transactional
    public void deleteChunks(Document document) {
        documentChunkRepository.deleteByDocument(document);
    }

    public List<DocumentChunk> getChunks(Document document) {
        return documentChunkRepository.findByDocumentOrderByChunkIndexAsc(document);
    }

    public List<Document> buildRelevantPromptDocuments(
            List<Document> documents,
            String requestedTitle,
            String instructions
    ) {
        Set<String> queryTerms = queryTerms(requestedTitle, instructions);

        return documents.stream()
                .map(document -> promptDocument(document, relevantText(document, queryTerms)))
                .toList();
    }

    private Document promptDocument(Document document, String relevantText) {
        Document promptDocument = new Document(
                document.getOriginalFileName(),
                document.getStoredFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getStorageUrl()
        );
        promptDocument.setOwner(document.getOwner());
        promptDocument.setUploadedAt(document.getUploadedAt());
        promptDocument.setExtractionStatus(document.getExtractionStatus());
        promptDocument.setExtractedText(relevantText);
        promptDocument.setTextExtractedAt(document.getTextExtractedAt());
        return promptDocument;
    }

    private String relevantText(Document document, Set<String> queryTerms) {
        List<DocumentChunk> chunks = getChunks(document);

        if (chunks.isEmpty()) {
            return document.getExtractedText();
        }

        if (queryTerms.isEmpty()) {
            return joinChunks(chunks.stream()
                    .limit(MAX_CHUNKS_FOR_AI)
                    .toList());
        }

        List<ScoredChunk> scoredChunks = chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, relevanceScore(chunk, queryTerms)))
                .filter(scoredChunk -> scoredChunk.score() > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparing(scoredChunk -> scoredChunk.chunk().getChunkIndex()))
                .limit(MAX_CHUNKS_FOR_AI)
                .sorted(Comparator.comparing(scoredChunk -> scoredChunk.chunk().getChunkIndex()))
                .toList();

        if (scoredChunks.isEmpty()) {
            return joinChunks(chunks.stream()
                    .limit(MAX_CHUNKS_FOR_AI)
                    .toList());
        }

        return joinChunks(scoredChunks.stream()
                .map(ScoredChunk::chunk)
                .toList());
    }

    private int relevanceScore(DocumentChunk chunk, Set<String> queryTerms) {
        String content = chunk.getContent().toLowerCase(Locale.ROOT);
        int score = 0;

        for (String queryTerm : queryTerms) {
            if (content.contains(queryTerm)) {
                score++;
            }
        }

        return score;
    }

    private Set<String> queryTerms(String requestedTitle, String instructions) {
        Set<String> terms = new LinkedHashSet<>();
        addQueryTerms(terms, requestedTitle);
        addQueryTerms(terms, instructions);
        return terms;
    }

    private void addQueryTerms(Set<String> terms, String value) {
        if (isBlank(value)) {
            return;
        }

        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 4 && !isStopWord(token)) {
                terms.add(token);
            }
        }
    }

    private boolean isStopWord(String token) {
        return Set.of(
                "with", "from", "that", "this", "into", "focus", "document", "documents",
                "standard", "operating", "procedure"
        ).contains(token);
    }

    private List<String> splitIntoChunks(String text) {
        String normalizedText = text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalizedText.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalizedText.length());
            chunks.add(normalizedText.substring(start, end).trim());

            if (end == normalizedText.length()) {
                break;
            }

            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }

        return chunks;
    }

    private String joinChunks(List<DocumentChunk> chunks) {
        StringBuilder text = new StringBuilder();

        for (DocumentChunk chunk : chunks) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("[Chunk ")
                    .append(chunk.getChunkIndex())
                    .append("]\n")
                    .append(chunk.getContent());
        }

        return text.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ScoredChunk(DocumentChunk chunk, int score) {
    }
}
