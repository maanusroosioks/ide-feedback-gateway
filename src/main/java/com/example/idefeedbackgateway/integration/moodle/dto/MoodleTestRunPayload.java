package com.example.idefeedbackgateway.integration.moodle.dto;

import com.example.idefeedbackgateway.model.Ide;

public record MoodleTestRunPayload(
        String userEmail,
        String assignmentKey,
        Ide ide,
        String projectName,
        String commitHash,
        String repoUrl,
        Long startedAtMs,
        Long finishedAtMs,
        MoodleRunListsPayload lists,
        Boolean captureDisabled
) {
}
