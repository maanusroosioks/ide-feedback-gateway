package com.example.idefeedbackgateway.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestResultRequest(
        @Size(max = 255) String testsuite,
        @NotBlank @Size(max = 255) String testname,
        @NotBlank @Size(max = 30) String status,
        @Min(0) Integer durationms,
        String message,
        @Size(max = 64) String stacktracehash
) {
}
