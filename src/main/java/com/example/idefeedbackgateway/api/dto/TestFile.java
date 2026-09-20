package com.example.idefeedbackgateway.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestFile(
        @NotBlank @Size(max = 512) String path,
        @Size(max = 64) String sha256,
        String content,
        Boolean truncated
) {
}
