package com.example.idefeedbackgateway.integration.moodle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodle")
public record MoodleProperties(String baseUrl, String token, String submitRunFunction, String validationErrorPrefix) {
}
