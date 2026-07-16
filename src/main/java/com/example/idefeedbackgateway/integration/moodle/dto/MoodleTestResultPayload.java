package com.example.idefeedbackgateway.integration.moodle.dto;

import com.example.idefeedbackgateway.model.TestResultStatus;

public record MoodleTestResultPayload(
        String testSuite,
        String testName,
        TestResultStatus status,
        Integer durationMs,
        String message,
        String stackTraceHash
) {
}
