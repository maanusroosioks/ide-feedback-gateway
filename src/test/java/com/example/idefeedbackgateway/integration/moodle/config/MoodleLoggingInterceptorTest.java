package com.example.idefeedbackgateway.integration.moodle.config;

import com.example.idefeedbackgateway.integration.moodle.MoodleClient;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleSubmitRunResponse;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestResultPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestRunPayload;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleIntegrationException;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class MoodleLoggingInterceptorTest {

    private static final String BASE_URL = "https://moodle.example.test";
    private static final String SECRET_TOKEN = "s3cret-moodle-token";

    private MockRestServiceServer server;
    private MoodleClient moodleClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestInterceptor(new MoodleLoggingInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        moodleClient = new MoodleClient(
                builder.build(),
                new MoodleProperties(BASE_URL, SECRET_TOKEN, "mod_idetestfeedback_submit_test_run", "validation_"),
                JsonMapper.builder().build());
    }

    @Test
    void redactsTheWebServiceTokenFromTheLoggedRequest(CapturedOutput output) {
        server.expect(request -> { }).andRespond(withSuccess("{\"runid\":42}", MediaType.APPLICATION_JSON));

        moodleClient.submitTestRun(payload());

        assertTrue(output.getOut().contains("wstoken=***"), output.getOut());
        assertFalse(output.getAll().contains(SECRET_TOKEN), "the Moodle token must never reach the logs");
    }

    /** The interceptor reads the body to log it; the client must still be able to read it after. */
    @Test
    void leavesTheResponseBodyReadableForTheClient(CapturedOutput output) {
        server.expect(request -> { })
                .andRespond(withSuccess("{\"runid\":42,\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertEquals(new MoodleSubmitRunResponse(42L, "ok"), moodleClient.submitTestRun(payload()));
        assertTrue(output.getOut().contains("{\"runid\":42,\"status\":\"ok\"}"), output.getOut());
    }

    @Test
    void leavesTheErrorBodyReadableForTheClient() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("maintenance mode"));

        MoodleTestRunPayload payload = payload();

        MoodleIntegrationException ex =
                assertThrows(MoodleIntegrationException.class, () -> moodleClient.submitTestRun(payload));
        assertTrue(ex.getMessage().contains("maintenance mode"), ex.getMessage());
    }

    private static MoodleTestRunPayload payload() {
        return new MoodleTestRunPayload(
                "student@taltech.ee", "assignment-101", Ide.VSCODE, null, null, null, null, null,
                List.of(new MoodleTestResultPayload(null, "test_add", TestResultStatus.PASSED, null, null, null)),
                List.of(), null);
    }
}
