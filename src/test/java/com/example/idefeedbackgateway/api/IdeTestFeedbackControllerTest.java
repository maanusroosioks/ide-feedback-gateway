package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestRunResponse;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import com.example.idefeedbackgateway.security.ApiSecurityErrorWriter;
import com.example.idefeedbackgateway.security.GraphAuthenticationEntryPoint;
import com.example.idefeedbackgateway.security.SecurityConfig;
import com.example.idefeedbackgateway.service.IdeTestFeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdeTestFeedbackController.class)
@Import({SecurityConfig.class, GraphAuthenticationEntryPoint.class, ApiSecurityErrorWriter.class})
class IdeTestFeedbackControllerTest {

    static final String RUNS_URL = "/api/v1/idetestfeedback/runs";
    static final String TOKEN = "valid-token";
    static final String EMAIL = "student@taltech.ee";

    static final String VALID_BODY = """
            {
              "assignmentKey": "assignment-101",
              "ide": "VSCODE",
              "projectName": "my-project",
              "startedAtMs": 1758134400000,
              "finishedAtMs": 1758134403120,
              "results": [
                {
                  "testName": "test_add",
                  "status": "PASSED",
                  "source": { "path": "tests/test_calc.py", "startLine": 3, "endLine": 4 }
                }
              ],
              "testFiles": [ { "path": "tests/test_calc.py", "content": "import pytest" } ],
              "captureDisabled": false
            }
            """;

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
    void submitsRunAndReturnsCreated() throws Exception {
        when(service.submitRun(any(), eq(EMAIL))).thenReturn(new TestRunResponse(42L, "ok"));

        mockMvc.perform(authorized(post(RUNS_URL)).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(42))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void passesTheBoundRequestAndTheTokensEmailToTheService() throws Exception {
        when(service.submitRun(any(), anyString())).thenReturn(new TestRunResponse(1L, "ok"));

        mockMvc.perform(authorized(post(RUNS_URL)).content(VALID_BODY)).andExpect(status().isCreated());

        ArgumentCaptor<TestRunRequest> request = ArgumentCaptor.forClass(TestRunRequest.class);
        verify(service).submitRun(request.capture(), eq(EMAIL));
        assertEquals("assignment-101", request.getValue().assignmentKey());
        assertEquals(Ide.VSCODE, request.getValue().ide());
        assertEquals(TestResultStatus.PASSED, request.getValue().results().getFirst().status());
        assertEquals(3, request.getValue().results().getFirst().source().startLine());
        assertEquals("import pytest", request.getValue().testFiles().getFirst().content());
    }

    @Test
    void acceptsFieldsThePluginSendsButMoodleDoesNotTake() throws Exception {
        when(service.submitRun(any(), anyString())).thenReturn(new TestRunResponse(1L, "ok"));
        String body = VALID_BODY
                .replace("\"captureDisabled\": false", "\"captureDisabled\": false, \"warningAcknowledged\": true")
                .replace("\"testName\": \"test_add\",", "\"testName\": \"test_add\", \"stackTraceHash\": \"9f86\",")
                .replace("\"startLine\": 3,", "\"startLine\": 3, \"kind\": \"TEST\", \"code\": \"x\",")
                .replace("\"path\": \"tests/test_calc.py\",", "\"path\": \"tests/test_calc.py\", \"sha256\": \"c7be\",");

        mockMvc.perform(authorized(post(RUNS_URL)).content(body)).andExpect(status().isCreated());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(post(RUNS_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsGetOnRunsEndpoint() throws Exception {
        mockMvc.perform(authorized(get(RUNS_URL)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deniesPathsOutsideTheApi() throws Exception {
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON);
    }
}
