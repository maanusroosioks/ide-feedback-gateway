package com.example.idefeedbackgateway.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "graph")
public record GraphProperties(
        @NotBlank String baseUrl,
        @NotBlank String tenantId,
        @NotNull Duration cacheTtl,
        @Positive long cacheMaxSize,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
