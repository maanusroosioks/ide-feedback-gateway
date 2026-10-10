package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.integration.moodle.exception.MoodleIntegrationException;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleValidationException;
import com.example.idefeedbackgateway.security.ApiSecurityErrorWriter;
import com.example.idefeedbackgateway.security.GraphAuthenticationEntryPoint;
import com.example.idefeedbackgateway.security.SecurityConfig;
import com.example.idefeedbackgateway.service.IdeTestFeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static com.example.idefeedbackgateway.api.IdeTestFeedbackControllerTest.EMAIL;
import static com.example.idefeedbackgateway.api.IdeTestFeedbackControllerTest.RUNS_URL;
import static com.example.idefeedbackgateway.api.IdeTestFeedbackControllerTest.TOKEN;
import static com.example.idefeedbackgateway.api.IdeTestFeedbackControllerTest.VALID_BODY;
import static com.example.idefeedbackgateway.api.IdeTestFeedbackControllerTest.authorized;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdeTestFeedbackController.class)
@Import({SecurityConfig.class, GraphAuthenticationEntryPoint.class, ApiSecurityErrorWriter.class})
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdeTestFeedbackService service;

    @MockitoBean
    private OpaqueTokenIntrospector introspector;

    @BeforeEach
    void acceptToken() {
        when(introspector.introspect(TOKEN)).thenReturn(
                new DefaultOAuth2AuthenticatedPrincipal(EMAIL, Map.of("upn", EMAIL), List.of()));
    }

    @Test
    void beanValidationFailureIsBadRequestWithFieldDetails() throws Exception {
        String body = VALID_BODY.replace("assignment-101", "lab 1.2");

        mockMvc.perform(authorized(post(RUNS_URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasItem(startsWith("assignmentKey: "))));

        verifyNoInteractions(service);
    }

    @Test
    void crossFieldValidationFailureNamesTheRule() throws Exception {
        String body = VALID_BODY.replace("\"endLine\": 4", "\"endLine\": 2");

        mockMvc.perform(authorized(post(RUNS_URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("results[0].source.lineRangeValid: "))));
    }

    @Test
    void missingResultsIsBadRequest() throws Exception {
        String body = """
                { "assignmentKey": "assignment-101", "ide": "VSCODE", "results": [] }
                """;

        mockMvc.perform(authorized(post(RUNS_URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("results: "))));
    }

    @Test
    void unparseableJsonIsMalformedRequest() throws Exception {
        mockMvc.perform(authorized(post(RUNS_URL)).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void unknownEnumValueListsTheAllowedValues() throws Exception {
        String body = VALID_BODY.replace("\"VSCODE\"", "\"ECLIPSE\"");

        mockMvc.perform(authorized(post(RUNS_URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details[0]").value("ide: must be one of [VSCODE]"));
    }

    @Test
    void moodleValidationFailureIsBadRequest() throws Exception {
        when(service.submitRun(any(), anyString()))
                .thenThrow(new MoodleValidationException("validation_windowclosed", "The submission window closed"));

        mockMvc.perform(authorized(post(RUNS_URL)).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MOODLE_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("The submission window closed"));
    }

    @Test
    void moodleFailureIsBadGateway() throws Exception {
        when(service.submitRun(any(), anyString()))
                .thenThrow(new MoodleIntegrationException("Moodle web service returned HTTP 500: oops"));

        mockMvc.perform(authorized(post(RUNS_URL)).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MOODLE_UNAVAILABLE"))
                .andExpect(jsonPath("$.errorMessage").value(containsString("HTTP 500")));
    }

    @Test
    void rejectedTokenIsUnauthorizedWithBearerChallenge() throws Exception {
        when(introspector.introspect("bad-token")).thenThrow(new BadOpaqueTokenException("Token belongs to a different tenant"));

        mockMvc.perform(post(RUNS_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void unreachableIdentityProviderIsServiceUnavailable() throws Exception {
        when(introspector.introspect("any-token"))
                .thenThrow(new OAuth2IntrospectionException("Microsoft Graph could not be reached"));

        mockMvc.perform(post(RUNS_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer any-token")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AUTH_UNAVAILABLE"));

        verifyNoInteractions(service);
    }
}
