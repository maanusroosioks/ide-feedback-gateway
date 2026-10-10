package com.example.idefeedbackgateway.api.dto;

import com.example.idefeedbackgateway.model.TestResultStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestResult(
        @Size(max = 255) String testSuite,
        @NotBlank @Size(max = 1024) String testName,
        @NotNull TestResultStatus status,
        @Min(0) Integer durationMs,
        String message,
        @Valid TestSource source
) {
}
