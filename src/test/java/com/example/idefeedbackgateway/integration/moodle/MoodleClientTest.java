package com.example.idefeedbackgateway.integration.moodle;

import com.example.idefeedbackgateway.integration.moodle.dto.MoodleRunListsPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleSubmitRunResponse;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestResultPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestRunPayload;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleIntegrationException;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleValidationException;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.example.idefeedbackgateway.integration.moodle.MoodleClientFixture.ENDPOINT;
import static com.example.idefeedbackgateway.integration.moodle.MoodleClientFixture.minimalRun;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * How the client talks to Moodle and reads its answers; the encoding of the run itself is
 * covered by {@link MoodleClientFormEncodingTest}.
 */
class MoodleClientTest {

    private static final MoodleTestRunPayload PAYLOAD = minimalRun(Ide.VSCODE);

    private MockRestServiceServer server;
    private MoodleClient moodleClient;

    @BeforeEach
    void setUp() {
        MoodleClientFixture moodle = MoodleClientFixture.create();
        server = moodle.server();
        moodleClient = moodle.client();
    }

    @Test
    void postsFormToTheRestEndpointWithTokenFunctionAndFormat() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "wstoken", "test-token",
                        "wsfunction", "mod_idetestfeedback_submit_test_run",
                        "moodlewsrestformat", "json",
                        "email", "student@taltech.ee",
                        "assignmentkey", "assignment-101",
                        "ide", "VSCODE")))
                .andRespond(success("{\"runid\":42}"));

        moodleClient.submitTestRun(PAYLOAD);

        server.verify();
    }

    @Test
    void leavesOutParametersThatAreNull() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    for (String absent : List.of("ide=", "projectname=", "commithash=", "repourl=", "startedatms=",
                            "finishedatms=", "capturedisabled=")) {
                        assertFalse(body.contains(absent), absent + " in " + body);
                    }
                })
                .andRespond(success("{\"runid\":42}"));

        moodleClient.submitTestRun(minimalRun(null));

        server.verify();
    }

    @Test
    void sendsRepoUrlAsTopLevelParameter() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().formDataContains(Map.of("repourl", "https://github.com/org/repo")))
                .andRespond(success("{\"runid\":42}"));

        moodleClient.submitTestRun(new MoodleTestRunPayload(
                "student@taltech.ee", "assignment-101", Ide.VSCODE, null, null, "https://github.com/org/repo",
                null, null,
                new MoodleRunListsPayload(
                        List.of(new MoodleTestResultPayload(null, "test_add", TestResultStatus.PASSED, null, null, null)),
                        List.of()),
                null));

        server.verify();
    }

    @Test
    void returnsRunIdAndStatusFromMoodle() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("{\"runid\":42,\"status\":\"queued\"}"));

        assertEquals(new MoodleSubmitRunResponse(42L, "queued"), moodleClient.submitTestRun(PAYLOAD));
    }

    @Test
    void defaultsStatusToOkWhenMoodleOnlyReturnsRunId() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("{\"runid\":42}"));

        assertEquals(new MoodleSubmitRunResponse(42L, "ok"), moodleClient.submitTestRun(PAYLOAD));
    }

    @Test
    void validationPrefixedErrorBecomesValidationException() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("""
                {"exception":"mod_idetestfeedback\\\\local\\\\validation_exception",
                 "errorcode":"validation_windowclosed","message":"The submission window closed"}
                """));

        MoodleValidationException ex = assertThrows(MoodleValidationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertEquals("validation_windowclosed", ex.errorCode());
        assertEquals("The submission window closed", ex.getMessage());
    }

    @Test
    void otherMoodleErrorBecomesIntegrationException() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("""
                {"exception":"invalid_parameter_exception","errorcode":"invalidparameter","message":"Invalid parameter"}
                """));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertFalse(ex instanceof MoodleValidationException);
        assertEquals("invalidparameter", ex.errorCode());
        assertEquals("Invalid parameter", ex.getMessage());
    }

    @Test
    void invalidTokenErrorIsNotAValidationError() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("""
                {"exception":"moodle_exception","errorcode":"invalidtoken","message":"Invalid token - token not found"}
                """));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertFalse(ex instanceof MoodleValidationException);
    }

    @Test
    void errorWithoutCodeOrMessageStillFails() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("{\"exception\":\"moodle_exception\"}"));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertNull(ex.errorCode());
        assertEquals("unknown Moodle error", ex.getMessage());
    }

    @Test
    void httpErrorStatusBecomesIntegrationExceptionWithBody() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("maintenance mode"));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertTrue(ex.getMessage().contains("503"), ex.getMessage());
        assertTrue(ex.getMessage().contains("maintenance mode"), ex.getMessage());
    }

    @Test
    void nonJsonResponseBecomesIntegrationException() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("<html>Notice: max_input_vars</html>", MediaType.TEXT_HTML));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertTrue(ex.getMessage().startsWith("Moodle returned a non-JSON response"), ex.getMessage());
    }

    @Test
    void emptyResponseBecomesIntegrationException() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertEquals("Moodle returned an empty response", ex.getMessage());
    }

    @Test
    void nonObjectResponseBecomesIntegrationException() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("[42]"));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertTrue(ex.getMessage().startsWith("Moodle returned an unexpected response shape"), ex.getMessage());
    }

    @Test
    void responseWithoutRunIdBecomesIntegrationException() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("{\"status\":\"ok\"}"));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertTrue(ex.getMessage().contains("missing 'runid'"), ex.getMessage());
    }

    @Test
    void malformedRunIdBecomesIntegrationException() {
        server.expect(requestTo(ENDPOINT)).andRespond(success("{\"runid\":\"not-a-number\"}"));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertInstanceOf(JacksonException.class, ex.getCause());
    }

    @Test
    void unreachableMoodleBecomesIntegrationException() {
        server.expect(requestTo(ENDPOINT)).andRespond(withException(new IOException("Connection refused")));

        MoodleIntegrationException ex = assertThrows(MoodleIntegrationException.class,
                () -> moodleClient.submitTestRun(PAYLOAD));
        assertTrue(ex.getMessage().startsWith("Moodle web service could not be reached"), ex.getMessage());
    }

    private static ResponseCreator success(String json) {
        return withSuccess(json, MediaType.APPLICATION_JSON);
    }
}
