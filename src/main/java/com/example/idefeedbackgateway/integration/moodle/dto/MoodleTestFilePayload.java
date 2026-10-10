package com.example.idefeedbackgateway.integration.moodle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MoodleTestFilePayload(
        @JsonProperty("path") String path,
        @JsonProperty("content") String content,
        @JsonProperty("truncated") Boolean truncated
) {
}
