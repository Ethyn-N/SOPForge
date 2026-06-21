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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        QueryContext queryContext = queryContext(requestedTitle, instructions);

        return documents.stream()
                .map(document -> promptDocument(document, relevantText(document, queryContext)))
                .toList();
    }

    public RelevancePreview buildRelevancePreview(
            List<Document> documents,
            String requestedTitle,
            String instructions
    ) {
        QueryContext queryContext = queryContext(requestedTitle, instructions);

        List<RelevanceChunk> relevanceChunks = documents.stream()
                .flatMap(document -> relevantChunks(document, queryContext).stream())
                .toList();

        return new RelevancePreview(queryContext.queryTermValues(), queryContext.phrases(), relevanceChunks);
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

    private String relevantText(Document document, QueryContext queryContext) {
        return joinChunks(relevantChunks(document, queryContext).stream()
                .map(RelevanceChunk::chunk)
                .toList());
    }

    private List<RelevanceChunk> relevantChunks(Document document, QueryContext queryContext) {
        List<DocumentChunk> chunks = getChunks(document);

        if (chunks.isEmpty()) {
            return List.of(new RelevanceChunk(
                    document,
                    new DocumentChunk(document, 0, document.getExtractedText()),
                    0,
                    0,
                    0,
                    List.of(),
                    List.of()
            ));
        }

        if (queryContext.isEmpty()) {
            return chunks.stream()
                    .limit(MAX_CHUNKS_FOR_AI)
                    .map(chunk -> new RelevanceChunk(document, chunk, 0, 0, 0, List.of(), List.of()))
                    .toList();
        }

        List<RelevanceChunk> scoredChunks = chunks.stream()
                .map(chunk -> scoreChunk(document, chunk, queryContext))
                .filter(scoredChunk -> scoredChunk.score() > 0)
                .sorted(Comparator.comparingInt(RelevanceChunk::score).reversed()
                        .thenComparing(Comparator.comparingInt(RelevanceChunk::phraseScore).reversed())
                        .thenComparing(scoredChunk -> scoredChunk.chunk().getChunkIndex()))
                .limit(MAX_CHUNKS_FOR_AI)
                .sorted(Comparator.comparing(scoredChunk -> scoredChunk.chunk().getChunkIndex()))
                .toList();

        if (scoredChunks.isEmpty()) {
            return chunks.stream()
                    .limit(MAX_CHUNKS_FOR_AI)
                    .map(chunk -> new RelevanceChunk(document, chunk, 0, 0, 0, List.of(), List.of()))
                    .toList();
        }

        return scoredChunks;
    }

    private RelevanceChunk scoreChunk(Document document, DocumentChunk chunk, QueryContext queryContext) {
        String content = normalize(chunk.getContent());
        List<String> matchedTerms = new ArrayList<>();
        List<String> matchedPhrases = new ArrayList<>();
        int baseScore = 0;

        for (QueryTerm queryTerm : queryContext.terms()) {
            int occurrences = occurrenceCount(content, queryTerm.value());
            if (occurrences > 0) {
                matchedTerms.add(queryTerm.value());
                baseScore += queryTerm.weight() * Math.min(occurrences, 3);
            }
        }

        for (String phrase : queryContext.phrases()) {
            int occurrences = occurrenceCount(content, phrase);
            if (occurrences > 0) {
                matchedPhrases.add(phrase);
            }
        }

        int phraseScore = matchedPhrases.size() * 5;

        return new RelevanceChunk(
                document,
                chunk,
                baseScore + phraseScore,
                baseScore,
                phraseScore,
                matchedTerms,
                matchedPhrases
        );
    }

    private QueryContext queryContext(String requestedTitle, String instructions) {
        Map<String, Integer> weightedTerms = new LinkedHashMap<>();
        addQueryTerms(weightedTerms, requestedTitle, 1);
        addQueryTerms(weightedTerms, instructions, 3);

        List<QueryTerm> queryTerms = weightedTerms.entrySet()
                .stream()
                .map(entry -> new QueryTerm(entry.getKey(), entry.getValue()))
                .toList();

        return new QueryContext(queryTerms, queryPhrases(instructions));
    }

    private void addQueryTerms(Map<String, Integer> terms, String value, int weight) {
        if (isBlank(value)) {
            return;
        }

        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 4 && !isStopWord(token)) {
                terms.merge(token, weight, Math::max);
            }
        }
    }

    private List<String> queryPhrases(String instructions) {
        List<String> phraseTokens = phraseTokens(instructions);
        Set<String> phrases = new LinkedHashSet<>();

        for (int phraseLength = 2; phraseLength <= 3; phraseLength++) {
            for (int index = 0; index <= phraseTokens.size() - phraseLength; index++) {
                phrases.add(String.join(" ", phraseTokens.subList(index, index + phraseLength)));
            }
        }

        return List.copyOf(phrases);
    }

    private List<String> phraseTokens(String value) {
        if (isBlank(value)) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();

        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 3 && !isPhraseStopWord(token)) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    private int occurrenceCount(String content, String query) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(query) + "\\b").matcher(content);
        int count = 0;

        while (matcher.find()) {
            count++;
        }

        return count;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
    }

    private boolean isStopWord(String token) {
        return Set.of(
                "with", "from", "that", "this", "into", "focus", "document", "documents",
                "standard", "operating", "procedure", "restaurant", "service", "business",
                "policy", "policies", "guideline", "guidelines", "responsibility",
                "responsibilities", "duties", "work"
        ).contains(token);
    }

    private boolean isPhraseStopWord(String token) {
        return Set.of(
                "and", "the", "for", "with", "from", "that", "this", "into", "focus",
                "document", "documents", "standard", "operating", "procedure"
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

    private record QueryContext(List<QueryTerm> terms, List<String> phrases) {

        private boolean isEmpty() {
            return terms.isEmpty() && phrases.isEmpty();
        }

        private List<String> queryTermValues() {
            return terms.stream()
                    .map(QueryTerm::value)
                    .toList();
        }
    }

    private record QueryTerm(String value, int weight) {
    }

    public record RelevancePreview(List<String> queryTerms, List<String> queryPhrases, List<RelevanceChunk> chunks) {
    }

    public record RelevanceChunk(
            Document document,
            DocumentChunk chunk,
            int score,
            int baseScore,
            int phraseScore,
            List<String> matchedTerms,
            List<String> matchedPhrases
    ) {
    }
}
