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
    private static final int MAX_CONTROL_CHUNKS_FOR_AI = 3;
    private static final Pattern CONTROL_EVIDENCE_PATTERN = Pattern.compile(
            "\\b(danger|warning|caution|hazard|emergency|must|required|shall|never|do not|only by|" +
                    "before starting|before servicing|verify|inspect|lockout|tagout|ppe|protective equipment|" +
                    "allergen|contamination|sanitize|sanitation|temperature|threshold|limit|maximum|minimum|" +
                    "stop work|out of service|report|escalate|record|document|checklist|sign-off|approval)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentEmbeddingService documentEmbeddingService;

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

        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);
        documentEmbeddingService.indexChunks(savedChunks);
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
        Map<Long, Double> semanticScores = semanticScores(documents, requestedTitle, instructions);

        return documents.stream()
                .map(document -> promptDocument(document, relevantText(document, queryContext, semanticScores)))
                .toList();
    }

    public RelevancePreview buildRelevancePreview(
            List<Document> documents,
            String requestedTitle,
            String instructions
    ) {
        QueryContext queryContext = queryContext(requestedTitle, instructions);
        Map<Long, Double> semanticScores = semanticScores(documents, requestedTitle, instructions);

        List<RelevanceChunk> relevanceChunks = documents.stream()
                .flatMap(document -> relevantChunks(document, queryContext, semanticScores).stream())
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

    private String relevantText(
            Document document,
            QueryContext queryContext,
            Map<Long, Double> semanticScores
    ) {
        return joinChunks(relevantChunks(document, queryContext, semanticScores).stream()
                .map(RelevanceChunk::chunk)
                .toList());
    }

    private List<RelevanceChunk> relevantChunks(
            Document document,
            QueryContext queryContext,
            Map<Long, Double> semanticScores
    ) {
        List<DocumentChunk> chunks = getChunks(document);

        if (chunks.isEmpty()) {
            return List.of(new RelevanceChunk(
                    document,
                    new DocumentChunk(document, 0, document.getExtractedText()),
                    0,
                    0,
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
                    .map(chunk -> new RelevanceChunk(document, chunk, 0, 0, 0, 0, 0, List.of(), List.of()))
                    .toList();
        }

        List<RelevanceChunk> scoredChunks = chunks.stream()
                .map(chunk -> scoreChunk(document, chunk, queryContext, semanticScores))
                .filter(scoredChunk -> scoredChunk.score() > 0)
                .sorted(Comparator.comparingInt(RelevanceChunk::score).reversed()
                        .thenComparing(Comparator.comparingInt(RelevanceChunk::phraseScore).reversed())
                        .thenComparing(scoredChunk -> scoredChunk.chunk().getChunkIndex()))
                .limit(MAX_CHUNKS_FOR_AI - MAX_CONTROL_CHUNKS_FOR_AI)
                .toList();

        Map<Integer, RelevanceChunk> selectedChunks = new LinkedHashMap<>();
        scoredChunks.forEach(chunk -> selectedChunks.put(chunk.chunk().getChunkIndex(), chunk));

        chunks.stream()
                .map(chunk -> new ControlChunk(chunk, controlEvidenceScore(chunk.getContent())))
                .filter(controlChunk -> controlChunk.score() > 0)
                .sorted(Comparator.comparingInt(ControlChunk::score).reversed()
                        .thenComparing(controlChunk -> controlChunk.chunk().getChunkIndex()))
                .limit(MAX_CONTROL_CHUNKS_FOR_AI)
                .map(controlChunk -> scoreChunk(document, controlChunk.chunk(), queryContext, semanticScores))
                .forEach(chunk -> selectedChunks.putIfAbsent(chunk.chunk().getChunkIndex(), chunk));

        if (selectedChunks.isEmpty()) {
            return chunks.stream()
                    .limit(MAX_CHUNKS_FOR_AI)
                    .map(chunk -> new RelevanceChunk(document, chunk, 0, 0, 0, 0, 0, List.of(), List.of()))
                    .toList();
        }

        return selectedChunks.values().stream()
                .limit(MAX_CHUNKS_FOR_AI)
                .sorted(Comparator.comparing(chunk -> chunk.chunk().getChunkIndex()))
                .toList();
    }

    private RelevanceChunk scoreChunk(
            Document document,
            DocumentChunk chunk,
            QueryContext queryContext,
            Map<Long, Double> semanticScores
    ) {
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
        int controlScore = controlEvidenceScore(chunk.getContent());
        double semanticScore = chunk.getId() == null
                ? 0.0
                : semanticScores.getOrDefault(chunk.getId(), 0.0);
        double keywordScore = Math.min(1.0, (baseScore + phraseScore) / 20.0);
        double normalizedControlScore = Math.min(1.0, controlScore / 5.0);
        int finalScore = (int) Math.round((
                semanticScore * 0.65
                        + keywordScore * 0.25
                        + normalizedControlScore * 0.10
        ) * 1000);

        return new RelevanceChunk(
                document,
                chunk,
                finalScore,
                baseScore,
                phraseScore,
                semanticScore,
                controlScore,
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

    private Map<Long, Double> semanticScores(
            List<Document> documents,
            String requestedTitle,
            String instructions
    ) {
        List<DocumentChunk> chunks = documents.stream()
                .flatMap(document -> getChunks(document).stream())
                .toList();
        String query = String.join(" ",
                isBlank(requestedTitle) ? "" : requestedTitle,
                isBlank(instructions) ? "" : instructions
        ).trim();
        return documentEmbeddingService.semanticScores(chunks, query);
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
        String[] blocks = normalizedText.split("\\n\\s*\\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String block : blocks) {
            String trimmedBlock = block.trim();
            if (trimmedBlock.isEmpty()) {
                continue;
            }

            if (trimmedBlock.length() > CHUNK_SIZE) {
                flushChunk(chunks, currentChunk);
                splitLongBlock(trimmedBlock, chunks);
                continue;
            }

            if (!currentChunk.isEmpty() && currentChunk.length() + 2 + trimmedBlock.length() > CHUNK_SIZE) {
                String overlap = trailingOverlap(currentChunk.toString());
                flushChunk(chunks, currentChunk);
                currentChunk.append(overlap);
            }

            if (!currentChunk.isEmpty()) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(trimmedBlock);
        }

        flushChunk(chunks, currentChunk);

        return chunks;
    }

    private void splitLongBlock(String block, List<String> chunks) {
        int start = 0;

        while (start < block.length()) {
            int desiredEnd = Math.min(start + CHUNK_SIZE, block.length());
            int end = sentenceBoundary(block, start, desiredEnd);
            chunks.add(block.substring(start, end).trim());

            if (end == block.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
    }

    private int sentenceBoundary(String text, int start, int desiredEnd) {
        if (desiredEnd == text.length()) {
            return desiredEnd;
        }

        int boundary = text.lastIndexOf(". ", desiredEnd);
        return boundary > start + CHUNK_SIZE / 2 ? boundary + 1 : desiredEnd;
    }

    private String trailingOverlap(String value) {
        if (value.length() <= CHUNK_OVERLAP) {
            return value;
        }
        return value.substring(value.length() - CHUNK_OVERLAP).trim();
    }

    private void flushChunk(List<String> chunks, StringBuilder chunk) {
        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString().trim());
            chunk.setLength(0);
        }
    }

    private int controlEvidenceScore(String content) {
        Matcher matcher = CONTROL_EVIDENCE_PATTERN.matcher(content == null ? "" : content);
        int score = 0;

        while (matcher.find()) {
            score++;
        }
        return score;
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

    private record ControlChunk(DocumentChunk chunk, int score) {
    }

    public record RelevancePreview(List<String> queryTerms, List<String> queryPhrases, List<RelevanceChunk> chunks) {
    }

    public record RelevanceChunk(
            Document document,
            DocumentChunk chunk,
            int score,
            int baseScore,
            int phraseScore,
            double semanticScore,
            int controlScore,
            List<String> matchedTerms,
            List<String> matchedPhrases
    ) {
    }
}
