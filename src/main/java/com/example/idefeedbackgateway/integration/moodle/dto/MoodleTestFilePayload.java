package com.example.idefeedbackgateway.integration.moodle.dto;

public record MoodleTestFilePayload(
        String path,
        String sha256,
        String content,
        Boolean truncated
) {
}
