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
        startOllamaStub("""
                {
                  "message": {
                    "role": "assistant",
                    "content": "```json\\n{\\n  \\"title\\": \\"Generated SOP\\",\\n  \\"purpose\\": \\"Create a safe process.\\",\\n  \\"scope\\": \\"Server operations.\\",\\n  \\"procedure\\": \\"Step one. Step two.\\",\\n  \\"roles\\": \\"Owner: ethyn@example.com\\"\\n}\\n```"
                  }
                }
                """);

        GeneratedSopDraft draft = new OllamaSopGenerator(aiProperties(), new ObjectMapper())
                .generate(document(), user());

        assertEquals("Generated SOP", draft.title());
        assertEquals("Create a safe process.", draft.purpose());
        assertEquals("Server operations.", draft.scope());
        assertEquals("Step one. Step two.", draft.procedure());
        assertEquals("Owner: ethyn@example.com", draft.roles());
    }

    private void startOllamaStub(String responseBody) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/chat", exchange -> {
            byte[] responseBytes = responseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        httpServer.start();
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
