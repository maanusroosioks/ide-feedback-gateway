package com.example.idefeedbackgateway.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "graph")
public record GraphProperties(
        String baseUrl,
        String tenantId,
        Duration cacheTtl,
        long cacheMaxSize,
        Duration connectTimeout,
        Duration readTimeout
) {
}
