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
        EvidenceBundle evidence = extractEvidence(
                restClient,
                documents,
                requestedTitle,
                instructions
        );

        OllamaChatRequest request = new OllamaChatRequest(
                aiProperties.getModel(),
                List.of(
                        new OllamaMessage("system", systemPrompt()),
                        new OllamaMessage("user", userPrompt(evidence, requestedTitle, instructions, user))
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

    private EvidenceBundle extractEvidence(
            RestClient restClient,
            List<Document> documents,
            String requestedTitle,
            String instructions
    ) {
        OllamaChatRequest request = new OllamaChatRequest(
                aiProperties.getModel(),
                List.of(
                        new OllamaMessage("system", evidenceSystemPrompt()),
                        new OllamaMessage("user", evidenceUserPrompt(
                                documents,
                                requestedTitle,
                                instructions
                        ))
                ),
                evidenceSchema(),
                false,
                ollamaOptions()
        );

        try {
            return parseEvidence(callOllama(restClient, request));
        } catch (RestClientException exception) {
            throw new IllegalStateException("AI evidence extraction failed. Make sure Ollama is running.");
        } catch (JsonProcessingException | InvalidEvidenceException exception) {
            log.warn("Ollama returned invalid evidence JSON. Problem: {}", exception.getMessage());
            throw new IllegalStateException("AI could not extract reliable evidence from the selected documents.");
        }
    }

    private String evidenceSystemPrompt() {
        return """
                Extract factual SOP requirements from business documents.
                Return exactly one JSON object matching the provided schema.
                Do not write an SOP and do not include markdown or commentary.
                Extract each requirement separately and preserve its subject, applicability, mandatory language,
                conditions, limits, timing, acceptance criteria, and source location.
                Treat danger, warning, caution, notice, must, shall, required, never, and do not statements as controls.
                Do not combine requirements for different products, roles, locations, or processes.
                Do not infer missing facts.
                Record contradictions in conflicts and missing essential SOP categories in unsupportedSections.
                """;
    }

    private String evidenceUserPrompt(
            List<Document> documents,
            String requestedTitle,
            String instructions
    ) {
        return """
                Extract only evidence relevant to this requested SOP.

                Requested title: %s
                User instructions: %s

                For every requirement, populate:
                - subject: applicable product, role, process, or location
                - phase: receiving, preparation, installation, operation, maintenance, shutdown, verification, or other
                - type: prerequisite, safety, action, acceptance criteria, frequency, record, escalation, or other
                - instruction: the supported requirement without weakening mandatory language
                - condition: when or under what circumstances it applies
                - acceptanceCriteria: exact threshold, measurement, expected state, or empty string
                - sourceFile: exact source file name
                - sourceLocation: Page N when available, otherwise Chunk N

                Selected source evidence:
                %s
                """.formatted(
                displayValue(requestedTitle),
                displayValue(instructions),
                sourceDocumentsText(documents)
        );
    }

    private String systemPrompt() {
        return """
                You generate evidence-grounded Standard Operating Procedures for any business domain.
                Return exactly one JSON object with these string fields:
                title, purpose, scope, procedure, roles.
                Do not include markdown or extra commentary.
                Do not wrap the JSON in backticks.
                Escape newlines inside JSON string values as \\n.
                The procedure field must contain a complete, sectioned SOP, not a summary or list of facts.
                Use only facts supported by the extracted document text.
                Preserve mandatory language, conditions, warnings, thresholds, intervals, and escalation requirements.
                Never combine unrelated products, roles, locations, or processes into one linear sequence.
                Never invent missing operational or safety details.
                When a necessary SOP section is unsupported, label it "Not specified in selected sources - review required."
                """;
    }

    private String userPrompt(EvidenceBundle evidence, String requestedTitle, String instructions, User user) {
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
                  "procedure": "APPLICABILITY\\n...\\n\\nPREREQUISITES\\n...\\n\\nSAFETY AND CONTROLS\\n...\\n\\nPROCEDURE\\n1. ...\\n\\nACCEPTANCE CRITERIA\\n...\\n\\nRECORDS\\n...\\n\\nEXCEPTIONS AND ESCALATION\\n...\\n\\nSOURCES\\n...",
                  "roles": "string"
                }

                Procedure requirements:
                - Use these headings in this order: APPLICABILITY, PREREQUISITES, SAFETY AND CONTROLS, PROCEDURE, ACCEPTANCE CRITERIA, RECORDS, EXCEPTIONS AND ESCALATION, SOURCES.
                - Under PROCEDURE, use numbered, action-oriented steps with enough detail to perform and verify the work.
                - Use subsection headings when sources describe different products, roles, phases, or processes.
                - Keep each instruction under the product or process to which it applies.
                - Do not create a false sequence from unrelated source facts.
                - Identify supported qualifications, authorization, tools, equipment, PPE, sanitation, and prerequisites.
                - Treat DANGER, WARNING, CAUTION, NOTICE, must, shall, required, never, and do not statements as mandatory controls.
                - Preserve exact limits, measurements, waiting periods, frequencies, temperatures, and pass/fail criteria.
                - Include supported pre-work checks, preparation, execution, verification, restoration, and closeout steps.
                - State supported stop-work conditions and who must be notified.
                - State supported records, checklists, readings, approvals, and sign-offs that must be retained.
                - Cite requirements as [Source: file name, Page N] when a page marker is available; otherwise use [Source: file name, Chunk N].
                - If the document is a job description, convert responsibilities into actual daily operating steps.
                - Do not add responsibilities that are not present in the source document.
                - If a required section is unsupported, write "Not specified in selected sources - review required" instead of guessing.
                - Synthesize only genuinely overlapping instructions from multiple documents.
                - If documents are unrelated, separate them into clearly labeled subsections or exclude irrelevant material.
                - If sources conflict, describe the conflict under EXCEPTIONS AND ESCALATION instead of choosing one silently.
                - Do not mix unrelated roles. If instructions narrow the topic, prioritize source facts relevant to those instructions.

                Validated structured evidence:
                %s
                """.formatted(
                displayValue(requestedTitle),
                user.getEmail(),
                displayValue(instructions),
                evidenceJson(evidence)
        );
    }

    private String evidenceJson(EvidenceBundle evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Extracted evidence could not be prepared for SOP generation.");
        }
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

    private Map<String, Object> evidenceSchema() {
        Map<String, Object> requirementProperties = new LinkedHashMap<>();
        for (String field : List.of(
                "subject",
                "phase",
                "type",
                "instruction",
                "condition",
                "acceptanceCriteria",
                "sourceFile",
                "sourceLocation"
        )) {
            requirementProperties.put(field, Map.of("type", "string"));
        }

        Map<String, Object> requirementSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.copyOf(requirementProperties.keySet()),
                "properties", requirementProperties
        );
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("requirements", "conflicts", "unsupportedSections"));
        schema.put("properties", Map.of(
                "requirements", Map.of("type", "array", "items", requirementSchema),
                "conflicts", Map.of("type", "array", "items", Map.of("type", "string")),
                "unsupportedSections", Map.of("type", "array", "items", Map.of("type", "string"))
        ));
        return schema;
    }

    private EvidenceBundle parseEvidence(String content) throws JsonProcessingException {
        EvidenceBundle evidence = objectMapper.readValue(extractJsonObject(content), EvidenceBundle.class);

        if (evidence.requirements() == null || evidence.requirements().isEmpty()) {
            throw new InvalidEvidenceException("Evidence extraction returned no requirements.");
        }

        List<EvidenceRequirement> requirements = evidence.requirements().stream()
                .filter(requirement -> requirement != null
                        && !isBlank(requirement.instruction())
                        && !isBlank(requirement.sourceFile()))
                .toList();
        if (requirements.isEmpty()) {
            throw new InvalidEvidenceException("Evidence extraction returned no supported requirements.");
        }

        return new EvidenceBundle(
                requirements,
                evidence.conflicts() == null ? List.of() : evidence.conflicts(),
                evidence.unsupportedSections() == null ? List.of() : evidence.unsupportedSections()
        );
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
                Preserve all safety controls, applicability labels, limits, citations, and required SOP sections from the original output.
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

    private record EvidenceBundle(
            List<EvidenceRequirement> requirements,
            List<String> conflicts,
            List<String> unsupportedSections
    ) {
    }

    private record EvidenceRequirement(
            String subject,
            String phase,
            String type,
            String instruction,
            String condition,
            String acceptanceCriteria,
            String sourceFile,
            String sourceLocation
    ) {
    }

    private static class InvalidGeneratedSopException extends RuntimeException {

        InvalidGeneratedSopException(String message) {
            super(message);
        }
    }

    private static class InvalidEvidenceException extends RuntimeException {

        InvalidEvidenceException(String message) {
            super(message);
        }
    }
}
