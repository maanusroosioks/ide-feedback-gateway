package com.example.idefeedbackgateway.integration.moodle.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public record MoodleSubmitRunResponse(
        Long runId,
        String status
) {

    public MoodleSubmitRunResponse {
        status = status != null ? status : "ok";
    }
}
