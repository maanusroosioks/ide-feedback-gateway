package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.integration.moodle.exception.MoodleIntegrationException;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;


import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest extends ApiWebMvcTest {

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
    void unknownEnumValueInsideAListNamesTheNestedField() throws Exception {
        String body = VALID_BODY.replace("\"PASSED\"", "\"PASS\"");

        mockMvc.perform(authorized(post(RUNS_URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("results[0].status: must be one of [")));
    }

    @Test
    void nullListEntriesAreValidationErrorsRatherThanServerErrors() throws Exception {
        String body = VALID_BODY.replace("\"testFiles\": [", "\"testFiles\": [ null,");

        mockMvc.perform(authorized(post(RUNS_URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasItem(startsWith("testFiles[0]"))));

        verifyNoInteractions(service);
    }

    @Test
    void wronglyTypedValueNamesTheField() throws Exception {
        String body = VALID_BODY.replace("1758134400000", "\"yesterday\"");

        mockMvc.perform(authorized(post(RUNS_URL)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details[0]").value("startedAtMs: is not a valid value"));

        verifyNoInteractions(service);
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

    /** Moodle's raw response can hold internal hosts or stack traces; it stays in the gateway's log. */
    @Test
    void moodleFailureIsBadGatewayWithoutMoodlesResponse() throws Exception {
        when(service.submitRun(any(), anyString()))
                .thenThrow(new MoodleIntegrationException("Moodle web service returned HTTP 500: <html>oops</html>"));

        mockMvc.perform(authorized(post(RUNS_URL)).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MOODLE_UNAVAILABLE"))
                .andExpect(jsonPath("$.errorMessage").value("Moodle could not accept the run"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("oops"))));
    }

    @Test
    void moodleErrorCodeIsPassedOnWithoutItsMessage() throws Exception {
        when(service.submitRun(any(), anyString()))
                .thenThrow(MoodleIntegrationException.fromMoodleError("invalidrecord", "Can't find data record in "
                        + "database table course_modules. SELECT * FROM {course_modules}"));

        mockMvc.perform(authorized(post(RUNS_URL)).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorMessage").value("Moodle could not accept the run"))
                .andExpect(jsonPath("$.details[0]").value("moodleErrorCode: invalidrecord"))
                .andExpect(content().string(not(containsString("course_modules"))));
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
