package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestRunResponse;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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

class IdeTestFeedbackControllerTest extends ApiWebMvcTest {

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
}
