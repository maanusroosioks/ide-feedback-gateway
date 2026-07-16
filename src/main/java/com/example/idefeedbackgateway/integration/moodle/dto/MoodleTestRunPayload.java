package com.example.idefeedbackgateway.integration.moodle.dto;

import com.example.idefeedbackgateway.model.Ide;

import java.util.List;

public record MoodleTestRunPayload(
        String userEmail,
        String assignmentKey,
        Ide ide,
        String projectName,
        String commitHash,
        Long startedAt,
        Long finishedAt,
        List<MoodleTestResultPayload> results
) {
}
