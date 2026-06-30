package com.securedoc.securedoc_ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "securedoc.ai.ollama")
public class AiProperties {

    private String baseUrl = "http://localhost:11434";
    private String model = "qwen2.5:14b";
    private String embeddingModel = "qwen3-embedding:0.6b";
    private int embeddingDimensions = 1024;
    private boolean semanticSearchEnabled = true;
}
