package com.securedoc.securedoc_ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "securedoc.storage")
public class StorageProperties {

    private String uploadDir = "uploads/documents";

}
