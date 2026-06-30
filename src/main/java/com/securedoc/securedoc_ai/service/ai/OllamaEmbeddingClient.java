package com.securedoc.securedoc_ai.service.ai;

import com.securedoc.securedoc_ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OllamaEmbeddingClient {

    private final AiProperties aiProperties;

    public List<float[]> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        EmbedResponse response = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .build()
                .post()
                .uri("/api/embed")
                .body(new EmbedRequest(aiProperties.getEmbeddingModel(), inputs))
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.embeddings() == null
                || response.embeddings().size() != inputs.size()) {
            throw new IllegalStateException("Ollama returned an invalid embedding response.");
        }

        return response.embeddings().stream()
                .map(this::toFloatArray)
                .toList();
    }

    private float[] toFloatArray(List<Double> values) {
        if (values == null || values.size() != aiProperties.getEmbeddingDimensions()) {
            throw new IllegalStateException("Ollama returned an embedding with unexpected dimensions.");
        }

        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            vector[index] = values.get(index).floatValue();
        }
        return vector;
    }

    private record EmbedRequest(String model, List<String> input) {
    }

    private record EmbedResponse(List<List<Double>> embeddings) {
    }
}
