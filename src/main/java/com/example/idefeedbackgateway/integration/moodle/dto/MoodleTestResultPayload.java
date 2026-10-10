package com.example.idefeedbackgateway.integration.moodle.dto;

import com.example.idefeedbackgateway.model.TestResultStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public record MoodleTestResultPayload(
        String testSuite,
        String testName,
        TestResultStatus status,
        Integer durationMs,
        String message,
        MoodleTestSourcePayload source
) {
}
