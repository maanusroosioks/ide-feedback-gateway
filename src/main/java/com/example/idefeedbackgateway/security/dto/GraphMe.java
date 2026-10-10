package com.example.idefeedbackgateway.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphMe(
        String id,
        String userPrincipalName,
        String mail,
        String displayName,
        String givenName,
        String surname
) {
}
