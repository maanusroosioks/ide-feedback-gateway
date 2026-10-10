package com.example.idefeedbackgateway.integration.moodle.dto;

import com.example.idefeedbackgateway.model.TestResultStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MoodleTestResultPayload(
        @JsonProperty("testsuite") String testSuite,
        @JsonProperty("testname") String testName,
        @JsonProperty("status") TestResultStatus status,
        @JsonProperty("durationms") Integer durationMs,
        @JsonProperty("message") String message,
        @JsonProperty("source") MoodleTestSourcePayload source
) {
}
