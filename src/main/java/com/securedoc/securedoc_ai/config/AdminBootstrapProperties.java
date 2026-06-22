package com.securedoc.securedoc_ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sopforge.admin.bootstrap")
public class AdminBootstrapProperties {

    private boolean enabled = false;
    private boolean resetPassword = false;
    private String email;
    private String password;
}
