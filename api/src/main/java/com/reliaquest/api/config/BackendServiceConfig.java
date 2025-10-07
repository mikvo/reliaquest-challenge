package com.reliaquest.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "backend")
public class BackendServiceConfig {
    private String url;
    private int maxRetries;
    private Duration retryBackoff;
    private Duration maxBackoff;
}
