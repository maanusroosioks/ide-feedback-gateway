package com.example.idefeedbackgateway.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestFile(
        @NotBlank @Size(max = 1024) String path,
        String content,
        Boolean truncated
) {
}
