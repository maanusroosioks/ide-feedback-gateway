package com.example.idefeedbackgateway.integration.moodle.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "moodle")
public record MoodleProperties(
        @NotBlank String baseUrl,
        @NotBlank String token,
        @NotBlank String submitRunFunction,
        @NotBlank String validationErrorPrefix,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    public boolean isValidationError(String errorCode) {
        return errorCode != null && errorCode.startsWith(validationErrorPrefix);
    }
}
