package com.example.idefeedbackgateway.integration.moodle;

import com.example.idefeedbackgateway.integration.moodle.config.MoodleProperties;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleRunListsPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestResultPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestRunPayload;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/** A {@link MoodleClient} that buffers content like the production one, talking to a mock Moodle. */
public record MoodleClientFixture(MockRestServiceServer server, MoodleClient client) {

    public static final String BASE_URL = "https://moodle.example.test";
    public static final String ENDPOINT = BASE_URL + "/webservice/rest/server.php";
    public static final String TOKEN = "test-token";

    public static MoodleClientFixture create() {
        return create(RestClient.builder(), TOKEN);
    }

    public static MoodleClientFixture create(RestClient.Builder builder, String token) {
        builder.baseUrl(BASE_URL).bufferContent((uri, method) -> true);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MoodleProperties properties = new MoodleProperties(BASE_URL, token, "mod_idetestfeedback_submit_test_run",
                "validation_", Duration.ofSeconds(5), Duration.ofSeconds(30));
        return new MoodleClientFixture(server, new MoodleClient(builder.build(), properties, JsonMapper.builder().build()));
    }

    /** One passing result and no captured files. */
    public static MoodleTestRunPayload minimalRun(Ide ide) {
        return new MoodleTestRunPayload(
                "student@taltech.ee", "assignment-101", ide, null, null, null, null, null,
                new MoodleRunListsPayload(
                        List.of(new MoodleTestResultPayload(null, "test_add", TestResultStatus.PASSED, null, null, null)),
                        List.of()),
                null);
    }
}
