package com.example.idefeedbackgateway.integration.moodle;

import com.example.idefeedbackgateway.integration.moodle.dto.MoodleRunListsPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestFilePayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestResultPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestRunPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestSourcePayload;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MoodleClientFormEncodingTest {

    /** wstoken, wsfunction, moodlewsrestformat, payload and the nine scalars of a full run. */
    private static final int EXPECTED_FORM_PARAMS = 13;

    private MockRestServiceServer server;
    private MoodleClient moodleClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        MoodleClientFixture moodle = MoodleClientFixture.create();
        server = moodle.server();
        moodleClient = moodle.client();
    }

    @Test
    void encodesScalarBooleansAsMoodleParamBoolValues() {
        expectRequest(body -> {
            assertTrue(body.contains("capturedisabled=1"), body);
            assertFalse(body.contains("capturedisabled=true"), "PARAM_BOOL rejects \"true\": " + body);
        });

        moodleClient.submitTestRun(payload(List.of(result()), List.of(testFile())));

        server.verify();
    }

    @Test
    void sendsListsAsOneJsonPayloadParameter() {
        expectRequest(body -> {
            assertFalse(body.contains("results%5B"), "results must not be flattened into form fields: " + body);
            assertFalse(body.contains("testfiles%5B"), "testfiles must not be flattened into form fields: " + body);

            JsonNode payload = jsonMapper.readTree(formValue(body, "payload"));
            JsonNode result = payload.path("results").get(0);
            assertEquals("test_add_returns_sum", result.path("testname").asString());
            assertEquals("test_calculator", result.path("testsuite").asString());
            assertEquals("PASSED", result.path("status").asString());
            assertEquals(12, result.path("durationms").asInt());
            assertEquals("boom", result.path("message").asString());

            JsonNode source = result.path("source");
            assertEquals("tests/test_calculator.py", source.path("path").asString());
            assertEquals(12, source.path("startline").asInt());
            assertEquals(13, source.path("endline").asInt());

            JsonNode testFile = payload.path("testfiles").get(0);
            assertEquals("tests/test_calculator.py", testFile.path("path").asString());
            assertEquals("import pytest", testFile.path("content").asString());
            assertTrue(testFile.path("truncated").isBoolean(), "PARAM_BOOL takes a JSON boolean");
        });

        moodleClient.submitTestRun(payload(List.of(result()), List.of(testFile())));

        server.verify();
    }

    /**
     * The point of the payload parameter: PHP counts every POST variable against max_input_vars,
     * so the parameter count has to stay flat as a run grows.
     */
    @Test
    void keepsParameterCountFlatAsTheRunGrows() {
        List<MoodleTestResultPayload> results = IntStream.range(0, 500).mapToObj(i -> result()).toList();

        expectRequest(body -> assertEquals(EXPECTED_FORM_PARAMS, body.split("&").length, body));

        moodleClient.submitTestRun(payload(results, List.of(testFile())));

        server.verify();
    }

    private void expectRequest(BodyAssertion assertion) {
        server.expect(method(HttpMethod.POST))
                .andExpect(request -> assertion.check(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"runid\":42,\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
    }

    private static String formValue(String body, String name) {
        return Arrays.stream(body.split("&"))
                .filter(pair -> pair.startsWith(name + "="))
                .map(pair -> URLDecoder.decode(pair.substring(name.length() + 1), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no '" + name + "' form parameter in: " + body));
    }

    private MoodleTestSourcePayload source() {
        return new MoodleTestSourcePayload("tests/test_calculator.py", 12, 13);
    }

    private MoodleTestResultPayload result() {
        return new MoodleTestResultPayload(
                "test_calculator", "test_add_returns_sum", TestResultStatus.PASSED, 12, "boom", source());
    }

    private MoodleTestFilePayload testFile() {
        return new MoodleTestFilePayload("tests/test_calculator.py", "import pytest", false);
    }

    private MoodleTestRunPayload payload(List<MoodleTestResultPayload> results,
                                         List<MoodleTestFilePayload> testFiles) {
        return new MoodleTestRunPayload(
                "student@taltech.ee", "assignment-101", Ide.VSCODE, "my-project", "a329d43f", "https://github.com/org/repo",
                1758134400000L, 1758134403120L, new MoodleRunListsPayload(results, testFiles), true);
    }

    @FunctionalInterface
    private interface BodyAssertion {
        void check(String body);
    }
}
