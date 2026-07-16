package com.example.idefeedbackgateway.integration.moodle;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class MoodleTestFeedbackClient {

    private final RestClient moodleRestClient;
    private final MoodleProperties properties;
    private final ObjectMapper objectMapper;

    public MoodleTestFeedbackClient(@Qualifier("moodleRestClient") RestClient moodleRestClient,
                                     MoodleProperties properties,
                                     ObjectMapper objectMapper) {
        this.moodleRestClient = moodleRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public MoodleSubmitRunResponse submitTestRun(MoodleTestRunPayload payload) {
        MultiValueMap<String, Object> form = toFormParams(payload);
        form.add("wstoken", properties.token());
        form.add("wsfunction", properties.submitRunFunction());
        form.add("moodlewsrestformat", "json");

        String rawResponse = moodleRestClient.post()
                .uri("/webservice/rest/server.php")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        return parseResponse(rawResponse);
    }

    private MoodleSubmitRunResponse parseResponse(String rawResponse) {
        JsonNode node;
        try {
            node = objectMapper.readTree(rawResponse);
        } catch (Exception e) {
            throw new MoodleIntegrationException("Moodle returned a non-JSON response: " + rawResponse, e);
        }

        if (node.has("exception")) {
            String message = node.path("message").asText(node.path("errorcode").asText("unknown Moodle error"));
            throw new MoodleIntegrationException("Moodle web service call failed: " + message);
        }

        Long runId = node.path("runid").isMissingNode() ? null : node.path("runid").asLong();
        String status = node.path("status").isMissingNode() ? "ok" : node.path("status").asText();
        return new MoodleSubmitRunResponse(runId, status);
    }

    // Moodle's REST protocol (webservice/rest/server.php) takes flat form params,
    // with nested arrays/objects expressed as name[index][field] pairs.
    private MultiValueMap<String, Object> toFormParams(MoodleTestRunPayload payload) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        putIfNotNull(form, "idetestfeedbackid", payload.idetestfeedbackid());
        putIfNotNull(form, "userid", payload.userid());
        putIfNotNull(form, "ide", payload.ide());
        putIfNotNull(form, "projectname", payload.projectname());
        putIfNotNull(form, "commithash", payload.commithash());
        putIfNotNull(form, "startedat", payload.startedat());
        putIfNotNull(form, "finishedat", payload.finishedat());
        putIfNotNull(form, "status", payload.status());
        putIfNotNull(form, "passedcount", payload.passedcount());
        putIfNotNull(form, "failedcount", payload.failedcount());
        putIfNotNull(form, "skippedcount", payload.skippedcount());
        putIfNotNull(form, "errorcount", payload.errorcount());

        List<MoodleTestResultPayload> results = payload.results();
        for (int i = 0; i < results.size(); i++) {
            MoodleTestResultPayload result = results.get(i);
            String prefix = "results[%d]".formatted(i);
            putIfNotNull(form, prefix + "[testsuite]", result.testsuite());
            putIfNotNull(form, prefix + "[testname]", result.testname());
            putIfNotNull(form, prefix + "[status]", result.status());
            putIfNotNull(form, prefix + "[durationms]", result.durationms());
            putIfNotNull(form, prefix + "[message]", result.message());
            putIfNotNull(form, prefix + "[stacktracehash]", result.stacktracehash());
        }
        return form;
    }

    private void putIfNotNull(MultiValueMap<String, Object> form, String key, Object value) {
        if (value != null) {
            form.add(key, value);
        }
    }
}
