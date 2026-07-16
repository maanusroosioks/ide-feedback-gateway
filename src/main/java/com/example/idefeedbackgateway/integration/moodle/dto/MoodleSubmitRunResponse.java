package com.example.idefeedbackgateway.integration.moodle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MoodleSubmitRunResponse(
        @JsonProperty("runid") Long runId,
        String status
) {
}
