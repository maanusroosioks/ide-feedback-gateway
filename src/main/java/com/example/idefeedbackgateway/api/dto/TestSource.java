package com.example.idefeedbackgateway.api.dto;

import com.example.idefeedbackgateway.model.SourceKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestSource(
        @NotNull SourceKind kind,
        @Size(max = 512) String filePath,
        @Min(1) Integer startLine,
        @Min(1) Integer endLine,
        String code,
        Boolean truncated,
        @Size(max = 64) String normalizedCodeHash
) {
}
