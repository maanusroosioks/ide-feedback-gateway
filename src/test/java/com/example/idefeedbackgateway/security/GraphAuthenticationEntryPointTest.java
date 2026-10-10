package com.example.idefeedbackgateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphAuthenticationEntryPointTest {

    private final GraphAuthenticationEntryPoint entryPoint =
            new GraphAuthenticationEntryPoint(new ApiSecurityErrorWriter(JsonMapper.builder().build()));

    @Test
    void answers401WithBearerChallengeForARejectedToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest("POST", "/api/v1/idetestfeedback/runs"), response,
                new InvalidBearerTokenException("bad token"));

        assertEquals(401, response.getStatus());
        assertNotNull(response.getHeader("WWW-Authenticate"), "RFC 6750 requires a bearer challenge");
        assertTrue(response.getHeader("WWW-Authenticate").startsWith("Bearer"));
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"), response.getContentAsString());
    }

    @Test
    void answers503WhenTheProviderItselfFailed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest("POST", "/api/v1/idetestfeedback/runs"), response,
                new AuthenticationServiceException("Microsoft Graph could not be reached"));

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_UNAVAILABLE"), response.getContentAsString());
    }
}
