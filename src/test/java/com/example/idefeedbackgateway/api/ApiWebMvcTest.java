package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.security.ApiSecurityErrorWriter;
import com.example.idefeedbackgateway.security.GraphAuthenticationEntryPoint;
import com.example.idefeedbackgateway.security.SecurityConfig;
import com.example.idefeedbackgateway.service.IdeTestFeedbackService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.Mockito.when;

/** The controller behind the real security chain, with the service and the Graph lookup mocked. */
@WebMvcTest(IdeTestFeedbackController.class)
@Import({SecurityConfig.class, GraphAuthenticationEntryPoint.class, ApiSecurityErrorWriter.class})
abstract class ApiWebMvcTest {

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
    protected MockMvc mockMvc;

    @MockitoBean
    protected IdeTestFeedbackService service;

    @MockitoBean
    protected OpaqueTokenIntrospector introspector;

    @BeforeEach
    void acceptToken() {
        when(introspector.introspect(TOKEN)).thenReturn(
                new DefaultOAuth2AuthenticatedPrincipal(EMAIL, Map.of("upn", EMAIL), List.of()));
    }

    static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON);
    }
}
