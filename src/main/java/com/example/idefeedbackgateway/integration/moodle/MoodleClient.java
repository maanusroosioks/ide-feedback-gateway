package com.example.idefeedbackgateway.integration.moodle;

import com.example.idefeedbackgateway.integration.moodle.config.MoodleProperties;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleRunListsPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleSubmitRunResponse;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestRunPayload;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleIntegrationException;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleValidationException;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Component
public class MoodleClient {

    private static final String REST_ENDPOINT = "/webservice/rest/server.php";
    private static final String PARAM_TOKEN = "wstoken";
    private static final String PARAM_FUNCTION = "wsfunction";
    private static final String PARAM_FORMAT = "moodlewsrestformat";
    private static final String PARAM_PAYLOAD = "payload";
    private static final String FORMAT_JSON = "json";
    private static final String FIELD_EXCEPTION = "exception";
    private static final String FIELD_ERRORCODE = "errorcode";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_RUNID = "runid";

    private final RestClient moodleRestClient;
    private final MoodleProperties properties;
    private final ObjectMapper objectMapper;

    public MoodleSubmitRunResponse submitTestRun(MoodleTestRunPayload payload) {
        MultiValueMap<String, String> form = toFormParams(payload);
        form.add(PARAM_TOKEN, properties.token());
        form.add(PARAM_FUNCTION, properties.submitRunFunction());
        form.add(PARAM_FORMAT, FORMAT_JSON);

        String rawResponse = moodleRestClient.post()
                .uri(REST_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new MoodleIntegrationException(
                            "Moodle web service returned HTTP %s: %s".formatted(response.getStatusCode(), body));
                })
                .body(String.class);

        return parseResponse(rawResponse);
    }

    private MoodleSubmitRunResponse parseResponse(String rawResponse) {
        JsonNode node = readTree(rawResponse);

        if (!node.isObject()) {
            throw new MoodleIntegrationException("Moodle returned an unexpected response shape: " + rawResponse);
        }

        if (node.has(FIELD_EXCEPTION)) {
            throw toErrorException(node);
        }

        return toSuccessResponse(node, rawResponse);
    }

    private JsonNode readTree(String rawResponse) {
        try {
            return objectMapper.readTree(rawResponse);
        } catch (JacksonException e) {
            throw new MoodleIntegrationException("Moodle returned a non-JSON response: " + rawResponse, e);
        }
    }

    private MoodleIntegrationException toErrorException(JsonNode node) {
        String errorCode = node.path(FIELD_ERRORCODE).asString(null);
        String message = node.path(FIELD_MESSAGE).asString("unknown Moodle error");

        if (MoodleValidationException.matches(errorCode, properties.validationErrorPrefix())) {
            return new MoodleValidationException(errorCode, message);
        }
        return new MoodleIntegrationException(errorCode, message);
    }

    private MoodleSubmitRunResponse toSuccessResponse(JsonNode node, String rawResponse) {
        if (!node.has(FIELD_RUNID)) {
            throw new MoodleIntegrationException("Moodle response is missing 'runid': " + rawResponse);
        }

        MoodleSubmitRunResponse result;
        try {
            result = objectMapper.treeToValue(node, MoodleSubmitRunResponse.class);
        } catch (JacksonException e) {
            throw new MoodleIntegrationException("Moodle returned a malformed success response: " + rawResponse, e);
        }
        return result.status() != null ? result : new MoodleSubmitRunResponse(result.runId(), "ok");
    }

    private MultiValueMap<String, String> toFormParams(MoodleTestRunPayload payload) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        putIfNotNull(form, "email", payload.userEmail());
        putIfNotNull(form, "assignmentkey", payload.assignmentKey());
        putIfNotNull(form, "ide", payload.ide());
        putIfNotNull(form, "projectname", payload.projectName());
        putIfNotNull(form, "commithash", payload.commitHash());
        putIfNotNull(form, "repourl", payload.repoUrl());
        putIfNotNull(form, "startedatms", payload.startedAtMs());
        putIfNotNull(form, "finishedatms", payload.finishedAtMs());
        putIfNotNull(form, "capturedisabled", payload.captureDisabled());
        form.add(PARAM_PAYLOAD, writeListsPayload(payload));
        return form;
    }

    /**
     * The run's lists go over as one parameter: flattened into form fields, every leaf counts
     * against PHP's {@code max_input_vars} and a large run is truncated before Moodle sees it.
     */
    private String writeListsPayload(MoodleTestRunPayload payload) {
        MoodleRunListsPayload lists = new MoodleRunListsPayload(payload.results(), payload.testFiles());
        try {
            return objectMapper.writeValueAsString(lists);
        } catch (JacksonException e) {
            throw new MoodleIntegrationException("Could not serialize the run payload", e);
        }
    }

    private void putIfNotNull(MultiValueMap<String, String> form, String key, Object value) {
        if (value != null) {
            form.add(key, toFormValue(value));
        }
    }

    /**
     * Moodle's {@code validate_param} compares a PARAM_BOOL value against {@code clean_param}'s
     * output as a string, so it only accepts "1"/"0" - "true" is rejected as an invalid parameter.
     */
    private String toFormValue(Object value) {
        return switch (value) {
            case Boolean flag -> flag ? "1" : "0";
            case Enum<?> constant -> constant.name();
            default -> value.toString();
        };
    }
}
