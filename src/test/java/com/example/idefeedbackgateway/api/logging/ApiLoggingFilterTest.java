package com.example.idefeedbackgateway.api.logging;

import ch.qos.logback.classic.Level;
import com.example.idefeedbackgateway.logging.LogLevels;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class ApiLoggingFilterTest {

    private static final String API_LOGGER = "com.example.idefeedbackgateway.api";

    private final ApiLoggingFilter filter = new ApiLoggingFilter();

    /** Caching the bodies for the log must not swallow them: the controller and the caller still see them. */
    @Test
    void passesBothBodiesThroughAndLogsThemAtDebug(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = request("{\"assignmentKey\":\"assignment-101\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> bodySeenByHandler = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            bodySeenByHandler.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            ((HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"runId\":42}");
        };

        try (LogLevels.Restore ignored = LogLevels.set(API_LOGGER, Level.DEBUG)) {
            filter.doFilter(request, response, chain);
        }

        assertEquals("{\"assignmentKey\":\"assignment-101\"}", bodySeenByHandler.get());
        assertEquals(201, response.getStatus());
        assertEquals("{\"runId\":42}", response.getContentAsString());
        assertTrue(output.getOut().contains("API REQUEST POST /api/v1/idetestfeedback/runs?dryRun=1 "
                + "body=[{\"assignmentKey\":\"assignment-101\"}]"), output.getOut());
        assertTrue(output.getOut().contains("-> 201"), output.getOut());
    }

    /** The request carries student source code, which students may have opted out of capturing. */
    @Test
    void keepsTheRequestBodyOutOfInfoLogs(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = request("{\"testFiles\":[{\"content\":\"secret_solution()\"}]}");
        FilterChain chain = (req, res) -> req.getInputStream().readAllBytes();

        try (LogLevels.Restore ignored = LogLevels.set(API_LOGGER, Level.INFO)) {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        assertTrue(output.getOut().contains("API REQUEST POST /api/v1/idetestfeedback/runs?dryRun=1"), output.getOut());
        assertFalse(output.getAll().contains("secret_solution"), output.getAll());
    }

    @Test
    void stillLogsWhenTheHandlerFails(CapturedOutput output) {
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };
        MockHttpServletRequest request = request("{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));
        assertTrue(output.getOut().contains("API RESPONSE POST /api/v1/idetestfeedback/runs"), output.getOut());
    }

    private static MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/idetestfeedback/runs");
        request.setQueryString("dryRun=1");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
