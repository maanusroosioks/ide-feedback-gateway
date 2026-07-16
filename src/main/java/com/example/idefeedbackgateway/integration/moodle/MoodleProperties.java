package com.example.idefeedbackgateway.integration.moodle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodle")
public record MoodleProperties(String baseUrl, String token, String submitRunFunction) {
}
