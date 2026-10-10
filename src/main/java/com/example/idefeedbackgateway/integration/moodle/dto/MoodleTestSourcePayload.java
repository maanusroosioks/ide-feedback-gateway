package com.example.idefeedbackgateway.integration.moodle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MoodleTestSourcePayload(
        @JsonProperty("path") String path,
        @JsonProperty("startline") Integer startLine,
        @JsonProperty("endline") Integer endLine
) {
}
