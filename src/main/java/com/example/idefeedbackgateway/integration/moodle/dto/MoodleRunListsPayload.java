package com.example.idefeedbackgateway.integration.moodle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public record MoodleRunListsPayload(
        List<MoodleTestResultPayload> results,
        List<MoodleTestFilePayload> testFiles
) {
}
