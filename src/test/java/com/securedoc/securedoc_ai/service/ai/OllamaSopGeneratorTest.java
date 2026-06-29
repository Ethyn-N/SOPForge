package com.securedoc.securedoc_ai.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securedoc.securedoc_ai.config.AiProperties;
import com.securedoc.securedoc_ai.model.Document;
import com.securedoc.securedoc_ai.model.User;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaSopGeneratorTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void generateParsesJsonWhenModelWrapsResponseInMarkdown() throws IOException {
        startOllamaStub(List.of(evidenceResponse(), """
                {
                  "message": {
                    "role": "assistant",
                    "content": "```json\\n{\\n  \\"title\\": \\"Generated SOP\\",\\n  \\"purpose\\": \\"Create a safe process.\\",\\n  \\"scope\\": \\"Server operations.\\",\\n  \\"procedure\\": \\"Step one. Step two.\\",\\n  \\"roles\\": \\"Owner: ethyn@example.com\\"\\n}\\n```"
                  }
                }
                """));

        GeneratedSopDraft draft = new OllamaSopGenerator(aiProperties(), new ObjectMapper())
                .generate(document(), user());

        assertEquals("Generated SOP", draft.title());
        assertEquals("Create a safe process.", draft.purpose());
        assertEquals("Server operations.", draft.scope());
        assertEquals("Step one. Step two.", draft.procedure());
        assertEquals("Owner: ethyn@example.com", draft.roles());
    }

    @Test
    void generateRepairsInvalidJsonResponse() throws IOException {
        startOllamaStub(List.of(
                evidenceResponse(),
                """
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\\"title\\": \\"Broken SOP\\", \\"purpose\\": \\"Purpose\\", \\"scope\\": \\"Scope\\", \\"procedure\\": \\"1. Broken step\\n2. Raw newline\\", \\"roles\\": \\"Server\\"}"
                          }
                        }
                        """,
                """
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\\"title\\": \\"Repaired SOP\\", \\"purpose\\": \\"Purpose\\", \\"scope\\": \\"Scope\\", \\"procedure\\": \\"1. Repaired step.\\\\n2. Valid step.\\", \\"roles\\": \\"Server\\"}"
                          }
                        }
                        """
        ));

        GeneratedSopDraft draft = new OllamaSopGenerator(aiProperties(), new ObjectMapper())
                .generate(document(), user());

        assertEquals("Repaired SOP", draft.title());
        assertEquals("1. Repaired step.\n2. Valid step.", draft.procedure());
        assertEquals("Server", draft.roles());
    }

    @Test
    void generateUsesRequestedTitleWhenModelOmitsTitle() throws IOException {
        startOllamaStub(List.of(
                evidenceResponse(),
                """
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\\"purpose\\": \\"Purpose\\", \\"scope\\": \\"Scope\\", \\"procedure\\": \\"1. Step one.\\", \\"roles\\": \\"Server\\"}"
                          }
                        }
                        """
        ));

        GeneratedSopDraft draft = new OllamaSopGenerator(aiProperties(), new ObjectMapper())
                .generate(List.of(document()), "Restaurant Service SOP", null, user());

        assertEquals("Restaurant Service SOP", draft.title());
        assertEquals("Purpose", draft.purpose());
        assertEquals("Scope", draft.scope());
        assertEquals("1. Step one.", draft.procedure());
        assertEquals("Server", draft.roles());
    }

    @Test
    void generateUsesDefaultsWhenModelOmitsMetadataFields() throws IOException {
        startOllamaStub(List.of(
                evidenceResponse(),
                """
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\\"procedure\\": \\"1. Follow the documented service process.\\"}"
                          }
                        }
                        """
        ));

        GeneratedSopDraft draft = new OllamaSopGenerator(aiProperties(), new ObjectMapper())
                .generate(List.of(document()), "Restaurant Service SOP", null, user());

        assertEquals("Restaurant Service SOP", draft.title());
        assertEquals("To define a clear standard operating procedure for Restaurant Service SOP.", draft.purpose());
        assertEquals("This SOP applies to the business process described in the selected source documents.", draft.scope());
        assertEquals("1. Follow the documented service process.", draft.procedure());
        assertEquals("server.pdf", draft.roles());
    }

    private void startOllamaStub(List<String> responseBodies) throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/chat", exchange -> {
            int responseIndex = Math.min(requestCount.getAndIncrement(), responseBodies.size() - 1);
            String responseBody = responseBodies.get(responseIndex);
            byte[] responseBytes = responseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        httpServer.start();
    }

    private String evidenceResponse() {
        return """
                {
                  "message": {
                    "role": "assistant",
                    "content": "{\\"requirements\\":[{\\"subject\\":\\"Server\\",\\"phase\\":\\"operation\\",\\"type\\":\\"action\\",\\"instruction\\":\\"Follow the documented service process.\\",\\"condition\\":\\"During service\\",\\"acceptanceCriteria\\":\\"\\",\\"sourceFile\\":\\"server.pdf\\",\\"sourceLocation\\":\\"Chunk 0\\"}],\\"conflicts\\":[],\\"unsupportedSections\\":[]}"
                  }
                }
                """;
    }

    private AiProperties aiProperties() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setBaseUrl("http://localhost:" + httpServer.getAddress().getPort());
        aiProperties.setModel("qwen2.5:14b");
        return aiProperties;
    }

    private Document document() {
        Document document = new Document(
                "server.pdf",
                "stored-server.pdf",
                "application/pdf",
                100L,
                "/uploads/stored-server.pdf"
        );
        document.setExtractedText("Server restart process.");
        return document;
    }

    private User user() {
        return new User("ethyn@example.com", "password");
    }
}
