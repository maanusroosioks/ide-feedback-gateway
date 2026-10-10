package com.example.idefeedbackgateway.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TestSource(
        @Size(max = 1024) String path,
        @Min(1) Integer startLine,
        @Min(1) Integer endLine
) {

    @AssertTrue(message = "startLine and endLine must both be set, in order, together with path")
    public boolean isLineRangeValid() {
        if (startLine == null && endLine == null) {
            return true;
        }
        return path != null && !path.isBlank()
                && startLine != null && endLine != null
                && endLine >= startLine;
    }
}
