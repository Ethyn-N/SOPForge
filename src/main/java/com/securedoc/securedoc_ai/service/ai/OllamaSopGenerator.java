package com.securedoc.securedoc_ai.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securedoc.securedoc_ai.config.AiProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@RequiredArgsConstructor
@Service
public class OllamaSopGenerator implements AiSopGenerator {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public GeneratedSopDraft generate(Document document, User user) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .build();

        OllamaChatRequest request = new OllamaChatRequest(
                aiProperties.getModel(),
                List.of(
                        new OllamaMessage("system", systemPrompt()),
                        new OllamaMessage("user", userPrompt(document, user))
                ),
                "json",
                false
        );

        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.message() == null || isBlank(response.message().content())) {
                throw new IllegalStateException("AI SOP generation returned an empty response.");
            }

            return parseGeneratedSop(response.message().content());
        } catch (RestClientException exception) {
            throw new IllegalStateException("AI SOP generation failed. Make sure Ollama is running.");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI SOP generation returned invalid JSON.");
        }
    }

    private String systemPrompt() {
        return """
                You generate Standard Operating Procedures for business documents.
                Return exactly one JSON object with these string fields:
                title, purpose, scope, procedure, roles.
                Do not include markdown or extra commentary.
                Do not wrap the JSON in backticks.
                The procedure field must be a detailed numbered list, not a short paragraph.
                Include at least 8 action-oriented steps when the source document has enough detail.
                Each procedure step should explain what to do, not only name a responsibility.
                Use only facts supported by the extracted document text.
                Do not invent timing, seating, customer greeting, inventory, or coordination details unless the source document says them.
                """;
    }

    private String userPrompt(Document document, User user) {
        return """
                Create a clear, professional SOP from this uploaded document.

                Source file: %s
                Owner email: %s

                Required response shape:
                {
                  "title": "string",
                  "purpose": "string",
                  "scope": "string",
                  "procedure": "1. First action step.\\n2. Second action step.\\n3. Continue with detailed action steps.",
                  "roles": "string"
                }

                Procedure requirements:
                - Write the procedure as a numbered list.
                - Prefer 8 to 15 steps when the document has enough information.
                - Start each step with an action verb.
                - Include operational details from the source document.
                - Do not compress the procedure into one paragraph.
                - If the document is a job description, convert responsibilities into actual daily operating steps.
                - Do not add responsibilities that are not present in the source document.
                - If a useful SOP detail is missing from the source document, omit it instead of guessing.

                Extracted document text:
                %s
                """.formatted(
                document.getOriginalFileName(),
                user.getEmail(),
                document.getExtractedText()
        );
    }

    private GeneratedSopDraft parseGeneratedSop(String content) throws JsonProcessingException {
        String json = extractJsonObject(content);
        JsonNode jsonNode = objectMapper.readTree(json);

        return new GeneratedSopDraft(
                textField(jsonNode, "title"),
                textField(jsonNode, "purpose"),
                textField(jsonNode, "scope"),
                textField(jsonNode, "procedure"),
                textField(jsonNode, "roles")
        );
    }

    private String textField(JsonNode jsonNode, String fieldName) {
        JsonNode field = jsonNode.get(fieldName);

        if (field == null || field.isNull()) {
            return null;
        }

        if (field.isTextual()) {
            return field.asText();
        }

        return field.toString();
    }

    private String extractJsonObject(String content) {
        String trimmedContent = content.trim();
        int objectStart = trimmedContent.indexOf('{');
        int objectEnd = trimmedContent.lastIndexOf('}');

        if (objectStart < 0 || objectEnd <= objectStart) {
            return trimmedContent;
        }

        return trimmedContent.substring(objectStart, objectEnd + 1);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record OllamaChatRequest(
            String model,
            List<OllamaMessage> messages,
            String format,
            boolean stream
    ) {
    }

    private record OllamaMessage(String role, String content) {
    }

    private record OllamaChatResponse(OllamaMessage message) {
    }
}
