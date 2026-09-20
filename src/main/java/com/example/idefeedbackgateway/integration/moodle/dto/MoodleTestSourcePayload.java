package com.example.idefeedbackgateway.integration.moodle.dto;

import com.example.idefeedbackgateway.model.SourceKind;

public record MoodleTestSourcePayload(
        SourceKind kind,
        String filePath,
        Integer startLine,
        Integer endLine,
        String code,
        Boolean truncated,
        String normalizedCodeHash
) {
}
