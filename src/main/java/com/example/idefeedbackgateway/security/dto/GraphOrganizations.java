package com.example.idefeedbackgateway.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphOrganizations(List<GraphOrganization> value) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphOrganization(String id, String displayName) {
    }
}
