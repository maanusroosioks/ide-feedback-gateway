package com.example.idefeedbackgateway.api.dto;

import com.example.idefeedbackgateway.model.Ide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TestRunRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9_-]*", message = "may only contain letters, digits, '_' and '-'")
        String assignmentKey,
        @NotNull Ide ide,
        @Size(max = 255) String projectName,
        @Size(max = 100) String commitHash,
        @Size(max = 1024) String repoUrl,
        @PositiveOrZero Long startedAtMs,
        @PositiveOrZero Long finishedAtMs,
        @NotEmpty @Size(max = 5000) List<@NotNull @Valid TestResult> results,
        @Size(max = 200) List<@NotNull @Valid TestFile> testFiles,
        Boolean captureDisabled
) {

    public static final int MAX_TOTAL_FILE_CONTENT_CHARS = 1_000_000;

    public TestRunRequest {
        testFiles = testFiles != null ? testFiles : List.of();
    }

    @AssertTrue(message = "finishedAtMs must not be before startedAtMs")
    public boolean isTimingOrdered() {
        return startedAtMs == null || finishedAtMs == null || finishedAtMs >= startedAtMs;
    }

    @AssertTrue(message = "the files' content must total at most " + MAX_TOTAL_FILE_CONTENT_CHARS + " characters")
    public boolean isFileContentWithinLimit() {
        long totalChars = testFiles.stream()
                .filter(file -> file != null && file.content() != null)
                .mapToLong(file -> file.content().length())
                .sum();
        return totalChars <= MAX_TOTAL_FILE_CONTENT_CHARS;
    }
}
