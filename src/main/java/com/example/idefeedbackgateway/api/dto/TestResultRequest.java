package com.example.idefeedbackgateway.api.dto;

import com.example.idefeedbackgateway.model.TestResultStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestResultRequest(
        @Size(max = 255) String testSuite,
        @NotBlank @Size(max = 255) String testName,
        @NotNull TestResultStatus status,
        @Min(0) Integer durationMs,
        String message,
        @Size(max = 64) String stackTraceHash
) {
}
