package com.example.idefeedbackgateway.integration.moodle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MoodleRunListsPayload(
        @JsonProperty("results") List<MoodleTestResultPayload> results,
        @JsonProperty("testfiles") List<MoodleTestFilePayload> testFiles
) {
}
