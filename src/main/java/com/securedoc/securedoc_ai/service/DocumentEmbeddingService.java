package com.securedoc.securedoc_ai.service;

import com.securedoc.securedoc_ai.config.AiProperties;
import com.securedoc.securedoc_ai.model.DocumentChunk;
import com.securedoc.securedoc_ai.service.ai.OllamaEmbeddingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEmbeddingService {

    private static final int EMBEDDING_BATCH_SIZE = 16;
    private static final int SEMANTIC_CANDIDATE_LIMIT = 200;

    private final OllamaEmbeddingClient embeddingClient;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final AiProperties aiProperties;

    public void indexChunks(List<DocumentChunk> chunks) {
        if (!aiProperties.isSemanticSearchEnabled() || chunks == null || chunks.isEmpty()) {
            return;
        }

        try {
            for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
                List<DocumentChunk> batch = chunks.subList(
                        start,
                        Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size())
                );
                List<float[]> embeddings = embeddingClient.embed(
                        batch.stream().map(DocumentChunk::getContent).toList()
                );

                for (int index = 0; index < batch.size(); index++) {
                    storeEmbedding(batch.get(index).getId(), embeddings.get(index));
                }
            }
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Document embeddings could not be generated; keyword retrieval remains available: {}",
                    exception.getMessage());
        }
    }

    public Map<Long, Double> semanticScores(List<DocumentChunk> chunks, String query) {
        if (!aiProperties.isSemanticSearchEnabled() || chunks == null || chunks.isEmpty()
                || query == null || query.isBlank()) {
            return Map.of();
        }

        try {
            indexChunks(missingEmbeddings(chunks));
            float[] queryEmbedding = embeddingClient.embed(List.of(query)).getFirst();
            return isPostgres()
                    ? postgresVectorScores(chunks, queryEmbedding)
                    : storedVectorScores(chunks, queryEmbedding);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Semantic retrieval is unavailable; using keyword retrieval: {}", exception.getMessage());
            return Map.of();
        }
    }

    private List<DocumentChunk> missingEmbeddings(List<DocumentChunk> chunks) {
        List<Long> ids = chunkIds(chunks);
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        // noinspection SqlResolve
        List<Long> embeddedIds = jdbcTemplate.queryForList(
                "SELECT id FROM document_chunks WHERE id IN (:ids) AND embedding IS NOT NULL",
                parameters,
                Long.class
        );
        return chunks.stream().filter(chunk -> !embeddedIds.contains(chunk.getId())).toList();
    }

    private Map<Long, Double> storedVectorScores(List<DocumentChunk> chunks, float[] queryEmbedding) {
        List<Long> ids = chunkIds(chunks);
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        // noinspection SqlResolve
        return jdbcTemplate.query(
                "SELECT id, embedding FROM document_chunks "
                        + "WHERE embedding IS NOT NULL AND id IN (:ids)",
                parameters,
                resultSet -> {
                    Map<Long, Double> scores = new HashMap<>();
                    while (resultSet.next()) {
                        scores.put(
                                resultSet.getLong("id"),
                                cosineSimilarity(queryEmbedding, parseVector(resultSet.getString("embedding")))
                        );
                    }
                    return scores;
                }
        );
    }

    private Map<Long, Double> postgresVectorScores(List<DocumentChunk> chunks, float[] queryEmbedding) {
        List<Long> ids = chunkIds(chunks);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("queryEmbedding", vectorString(queryEmbedding))
                .addValue("ids", ids)
                .addValue("limit", Math.min(ids.size(), SEMANTIC_CANDIDATE_LIMIT));

        // noinspection SqlResolve
        return jdbcTemplate.query(
                "SELECT chunk.id, 1 - (chunk.embedding OPERATOR(extensions.<=>) query.embedding) AS similarity "
                        + "FROM document_chunks chunk "
                        + "CROSS JOIN (SELECT CAST(:queryEmbedding AS extensions.vector) AS embedding) query "
                        + "WHERE chunk.embedding IS NOT NULL AND chunk.id IN (:ids) "
                        + "ORDER BY chunk.embedding OPERATOR(extensions.<=>) query.embedding LIMIT :limit",
                parameters,
                resultSet -> {
                    Map<Long, Double> scores = new HashMap<>();
                    while (resultSet.next()) {
                        scores.put(
                                resultSet.getLong("id"),
                                clampSimilarity(resultSet.getDouble("similarity"))
                        );
                    }
                    return scores;
                }
        );
    }

    private void storeEmbedding(Long chunkId, float[] embedding) {
        if (chunkId == null) {
            return;
        }

        if (isPostgres()) {
            // noinspection SqlResolve
            jdbcTemplate.update(
                    "UPDATE document_chunks SET embedding = CAST(:embedding AS extensions.vector) WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("embedding", vectorString(embedding))
                            .addValue("id", chunkId)
            );
        } else {
            // noinspection SqlResolve
            jdbcTemplate.update(
                    "UPDATE document_chunks SET embedding = :embedding WHERE id = :id",
                    new MapSqlParameterSource()
                            .addValue("embedding", vectorString(embedding))
                            .addValue("id", chunkId)
            );
        }
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(Locale.ROOT)
                    .contains("postgresql");
        } catch (SQLException exception) {
            throw new IllegalStateException("Database type could not be determined.", exception);
        }
    }

    private List<Long> chunkIds(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(DocumentChunk::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String vectorString(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) value.append(',');
            value.append(vector[index]);
        }
        return value.append(']').toString();
    }

    private float[] parseVector(String value) {
        String normalizedValue = value.trim();
        String[] values = normalizedValue.substring(1, normalizedValue.length() - 1).split(",");
        float[] vector = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            vector[index] = Float.parseFloat(values[index]);
        }
        return vector;
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) return 0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return clampSimilarity(dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }

    private double clampSimilarity(double similarity) {
        return Math.clamp(similarity, 0, 1);
    }
}
