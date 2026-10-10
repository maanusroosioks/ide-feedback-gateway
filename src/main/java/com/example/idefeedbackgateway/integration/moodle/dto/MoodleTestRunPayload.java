package com.example.idefeedbackgateway.integration.moodle.dto;

import com.example.idefeedbackgateway.model.Ide;

import java.util.List;

public record MoodleTestRunPayload(
        String userEmail,
        String assignmentKey,
        Ide ide,
        String projectName,
        String commitHash,
        String repoUrl,
        Long startedAtMs,
        Long finishedAtMs,
        List<MoodleTestResultPayload> results,
        List<MoodleTestFilePayload> testFiles,
        Boolean captureDisabled
) {
}
