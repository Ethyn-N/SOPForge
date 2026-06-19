package com.securedoc.securedoc_ai.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securedoc.securedoc_ai.config.AiProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class OllamaSopGenerator implements AiSopGenerator {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public GeneratedSopDraft generate(List<Document> documents, String requestedTitle, String instructions, User user) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .build();

        OllamaChatRequest request = new OllamaChatRequest(
                aiProperties.getModel(),
                List.of(
                        new OllamaMessage("system", systemPrompt()),
                        new OllamaMessage("user", userPrompt(documents, requestedTitle, instructions, user))
                ),
                responseSchema(),
                false,
                ollamaOptions()
        );

        String generatedContent;

        try {
            generatedContent = callOllama(restClient, request);
        } catch (RestClientException exception) {
            throw new IllegalStateException("AI SOP generation failed. Make sure Ollama is running.");
        }

        try {
            return parseAndValidateGeneratedSop(generatedContent, requestedTitle, documents);
        } catch (JsonProcessingException exception) {
            return repairAndParseGeneratedSop(restClient, exception.getOriginalMessage(), generatedContent, request, requestedTitle, documents);
        } catch (InvalidGeneratedSopException exception) {
            return repairAndParseGeneratedSop(restClient, exception.getMessage(), generatedContent, request, requestedTitle, documents);
        }
    }

    private String systemPrompt() {
        return """
                You generate Standard Operating Procedures for business documents.
                Return exactly one JSON object with these string fields:
                title, purpose, scope, procedure, roles.
                Do not include markdown or extra commentary.
                Do not wrap the JSON in backticks.
                Escape newlines inside JSON string values as \\n.
                The procedure field must be a detailed numbered list, not a short paragraph.
                Include at least 8 action-oriented steps when the source document has enough detail.
                Each procedure step should explain what to do, not only name a responsibility.
                Use only facts supported by the extracted document text.
                Do not invent timing, seating, customer greeting, inventory, or coordination details unless the source document says them.
                """;
    }

    private String userPrompt(List<Document> documents, String requestedTitle, String instructions, User user) {
        return """
                Create a clear, professional SOP from the selected uploaded document sources.

                Requested title: %s
                Owner email: %s
                User instructions: %s

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
                - When multiple source documents are provided, synthesize overlapping information into one coherent SOP.
                - Do not mix unrelated roles. If instructions narrow the topic, prioritize source facts relevant to those instructions.

                Extracted document sources:
                %s
                """.formatted(
                displayValue(requestedTitle),
                user.getEmail(),
                displayValue(instructions),
                sourceDocumentsText(documents)
        );
    }

    private String sourceDocumentsText(List<Document> documents) {
        StringBuilder sourceText = new StringBuilder();

        for (Document document : documents) {
            sourceText.append("Source file: ")
                    .append(document.getOriginalFileName())
                    .append("\n")
                    .append(document.getExtractedText())
                    .append("\n\n");
        }

        return sourceText.toString();
    }

    private String displayValue(String value) {
        if (isBlank(value)) {
            return "not specified";
        }

        return value;
    }

    private String callOllama(RestClient restClient, OllamaChatRequest request) {
        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null || isBlank(response.message().content())) {
            throw new IllegalStateException("AI SOP generation returned an empty response.");
        }

        return response.message().content();
    }

    private GeneratedSopDraft repairAndParseGeneratedSop(
            RestClient restClient,
            String parseError,
            String invalidContent,
            OllamaChatRequest originalRequest,
            String requestedTitle,
            List<Document> documents
    ) {
        OllamaChatRequest repairRequest = new OllamaChatRequest(
                aiProperties.getModel(),
                List.of(
                        new OllamaMessage("system", jsonRepairSystemPrompt()),
                        new OllamaMessage("user", jsonRepairUserPrompt(parseError, invalidContent, originalRequest))
                ),
                responseSchema(),
                false,
                ollamaOptions()
        );

        String repairContent;

        try {
            repairContent = callOllama(restClient, repairRequest);
        } catch (RestClientException exception) {
            throw new IllegalStateException("AI SOP generation failed. Make sure Ollama is running.");
        }

        try {
            return parseAndValidateGeneratedSop(repairContent, requestedTitle, documents);
        } catch (JsonProcessingException exception) {
            log.warn("Ollama returned invalid SOP JSON after repair. Response preview: {}", preview(repairContent));
            throw new IllegalStateException("AI SOP generation returned invalid JSON.");
        } catch (InvalidGeneratedSopException exception) {
            log.warn("Ollama returned incomplete SOP JSON after repair. Problem: {}. Response preview: {}",
                    exception.getMessage(),
                    preview(repairContent));
            throw new IllegalStateException(exception.getMessage());
        }
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("title", "purpose", "scope", "procedure", "roles"));
        schema.put("properties", Map.of(
                "title", Map.of("type", "string"),
                "purpose", Map.of("type", "string"),
                "scope", Map.of("type", "string"),
                "procedure", Map.of("type", "string"),
                "roles", Map.of("type", "string")
        ));
        return schema;
    }

    private Map<String, Object> ollamaOptions() {
        return Map.of(
                "temperature", 0.1,
                "num_ctx", 32768
        );
    }

    private String jsonRepairSystemPrompt() {
        return """
                Convert invalid or incomplete SOP output into exactly one valid JSON object.
                Return only JSON with these string fields:
                title, purpose, scope, procedure, roles.
                All five fields are required and must be non-empty strings.
                Escape newlines inside string values as \\n.
                Do not include markdown, comments, or explanation.
                """;
    }

    private String jsonRepairUserPrompt(String parseError, String invalidContent, OllamaChatRequest originalRequest) {
        return """
                The previous SOP response was invalid.

                Problem:
                %s

                Invalid SOP output:
                %s

                If possible, repair that invalid output. If it cannot be repaired, regenerate the SOP as valid JSON using the same original request:
                %s
                """.formatted(parseError, invalidContent, originalRequest.messages());
    }

    private GeneratedSopDraft parseAndValidateGeneratedSop(
            String content,
            String requestedTitle,
            List<Document> documents
    ) throws JsonProcessingException {
        GeneratedSopDraft generatedSopDraft = parseGeneratedSop(content);
        generatedSopDraft = normalizeGeneratedSop(generatedSopDraft, requestedTitle, documents);
        validateGeneratedSop(generatedSopDraft);
        return generatedSopDraft;
    }

    private GeneratedSopDraft normalizeGeneratedSop(
            GeneratedSopDraft generatedSopDraft,
            String requestedTitle,
            List<Document> documents
    ) {
        String title = generatedSopDraft.title();
        String purpose = generatedSopDraft.purpose();
        String scope = generatedSopDraft.scope();
        String roles = generatedSopDraft.roles();

        if (isBlank(title) && !isBlank(requestedTitle)) {
            title = requestedTitle;
        }

        if (isBlank(purpose) && !isBlank(title)) {
            purpose = "To define a clear standard operating procedure for " + title + ".";
        }

        if (isBlank(scope)) {
            scope = "This SOP applies to the business process described in the selected source documents.";
        }

        if (isBlank(roles)) {
            roles = sourceDocumentNames(documents);
        }

        return new GeneratedSopDraft(
                title,
                purpose,
                scope,
                generatedSopDraft.procedure(),
                roles
        );
    }

    private String sourceDocumentNames(List<Document> documents) {
        StringBuilder names = new StringBuilder();

        for (Document document : documents) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(document.getOriginalFileName());
        }

        if (names.isEmpty()) {
            return "Not specified";
        }

        return names.toString();
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

    private void validateGeneratedSop(GeneratedSopDraft generatedSopDraft) {
        requireGenerated(generatedSopDraft.title(), "title");
        requireGenerated(generatedSopDraft.purpose(), "purpose");
        requireGenerated(generatedSopDraft.scope(), "scope");
        requireGenerated(generatedSopDraft.procedure(), "procedure");
        requireGenerated(generatedSopDraft.roles(), "roles");
    }

    private void requireGenerated(String value, String fieldName) {
        if (isBlank(value)) {
            throw new InvalidGeneratedSopException("AI SOP generation did not return " + fieldName + ".");
        }
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

    private String preview(String value) {
        if (value == null) {
            return "";
        }

        String compactValue = value.replaceAll("\\s+", " ").trim();

        if (compactValue.length() <= 300) {
            return compactValue;
        }

        return compactValue.substring(0, 300) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record OllamaChatRequest(
            String model,
            List<OllamaMessage> messages,
            Map<String, Object> format,
            boolean stream,
            Map<String, Object> options
    ) {
    }

    private record OllamaMessage(String role, String content) {
    }

    private record OllamaChatResponse(OllamaMessage message) {
    }

    private static class InvalidGeneratedSopException extends RuntimeException {

        InvalidGeneratedSopException(String message) {
            super(message);
        }
    }
}
