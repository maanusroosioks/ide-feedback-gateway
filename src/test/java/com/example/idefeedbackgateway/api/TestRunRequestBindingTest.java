package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.TestFile;
import com.example.idefeedbackgateway.api.dto.TestResult;
import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestSource;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunRequestBindingTest {

    private static final String PAYLOAD = """
            {
              "assignmentKey": "assignment-101",
              "ide": "VSCODE",
              "projectName": "my-project",
              "commitHash": "a329d43f8b1e",
              "repoUrl": "https://github.com/org/repo",
              "startedAtMs": 1758134400000,
              "finishedAtMs": 1758134403120,
              "results": [
                {
                  "testSuite": "test_calculator",
                  "testName": "test_add_returns_sum",
                  "status": "PASSED",
                  "durationMs": 12,
                  "message": "AssertionError: assert 4 == 5",
                  "stackTraceHash": "9f86d081884c7d65",
                  "source": {
                    "kind": "TEST",
                    "path": "tests/test_calculator.py",
                    "startLine": 12,
                    "endLine": 13,
                    "code": "def test_add_returns_sum():\\n    assert add(2, 3) == 5",
                    "truncated": false,
                    "normalizedCodeHash": "b1946ac92492d234"
                  }
                }
              ],
              "testFiles": [
                {
                  "path": "tests/test_calculator.py",
                  "sha256": "c7be1ed902fb8dd4",
                  "content": "import pytest\\n\\ndef test_add_returns_sum():\\n    assert add(2, 3) == 5\\n",
                  "truncated": false
                }
              ],
              "captureDisabled": true,
              "warningAcknowledged": true
            }
            """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /** The IDE plugin sends fields Moodle has no place for; they must not break binding. */
    @Test
    void bindsFullPayloadIgnoringFieldsMoodleDoesNotTake() {
        TestRunRequest request = jsonMapper.readValue(PAYLOAD, TestRunRequest.class);

        assertEquals("assignment-101", request.assignmentKey());
        assertEquals(Ide.VSCODE, request.ide());
        assertEquals("https://github.com/org/repo", request.repoUrl());
        assertEquals(1758134400000L, request.startedAtMs());
        assertEquals(Boolean.TRUE, request.captureDisabled());

        TestResult result = request.results().getFirst();
        assertEquals(TestResultStatus.PASSED, result.status());

        TestSource source = result.source();
        assertEquals("tests/test_calculator.py", source.path());
        assertEquals(12, source.startLine());
        assertEquals(13, source.endLine());

        TestFile testFile = request.testFiles().getFirst();
        assertEquals("tests/test_calculator.py", testFile.path());
        assertEquals(Boolean.FALSE, testFile.truncated());
        assertTrue(testFile.content().startsWith("import pytest"));
    }

    @Test
    void fullPayloadPassesValidation() {
        TestRunRequest request = jsonMapper.readValue(PAYLOAD, TestRunRequest.class);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<TestRunRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty(), () -> "unexpected violations: " + violations);
        }
    }

    @Test
    void rejectsAssignmentKeyMoodleWouldNotAccept() {
        assertViolation(PAYLOAD.replace("assignment-101", "lab 1.2"), "assignmentKey");
    }

    @Test
    void acceptsTestNamesUpToMoodlesColumnWidth() {
        assertViolations(PAYLOAD.replace("test_add_returns_sum", "t".repeat(1024)), Set.of());
        assertViolation(PAYLOAD.replace("test_add_returns_sum", "t".repeat(1025)), "results[0].testName");
    }

    @Test
    void acceptsMessagesUpToThePluginsCap() {
        assertViolations(PAYLOAD.replace("AssertionError: assert 4 == 5", "m".repeat(4000)), Set.of());
        assertViolation(PAYLOAD.replace("AssertionError: assert 4 == 5", "m".repeat(4001)), "results[0].message");
    }

    /** No single file is over the cap here; together they are. */
    @Test
    void capsTheTotalContentAcrossFiles() {
        assertViolations(withExtraFile(PAYLOAD, 900_000), Set.of());
        assertViolation(withExtraFile(withExtraFile(PAYLOAD, 500_000), 500_000), "fileContentWithinLimit");
    }

    @Test
    void rejectsNullListEntries() {
        assertViolation(PAYLOAD.replace("\"results\": [", "\"results\": [ null,"), "results[0].<list element>");
        assertViolation(PAYLOAD.replace("\"testFiles\": [", "\"testFiles\": [ null,"), "testFiles[0].<list element>");
    }

    @Test
    void rejectsRepoUrlLongerThanMoodlesColumn() {
        assertViolation(PAYLOAD.replace("https://github.com/org/repo", "https://github.com/" + "r".repeat(1010)),
                "repoUrl");
    }

    @Test
    void rejectsRunThatFinishesBeforeItStarts() {
        assertViolation(PAYLOAD.replace("1758134403120", "1758134300000"), "timingOrdered");
    }

    @Test
    void rejectsNegativeTimestamps() {
        assertViolation(PAYLOAD.replace("1758134400000", "-1"), "startedAtMs");
    }

    @Test
    void rejectsHalfALineRange() {
        assertViolation(PAYLOAD.replace("\"endLine\": 13,", ""), "results[0].source.lineRangeValid");
    }

    @Test
    void rejectsReversedLineRange() {
        assertViolation(PAYLOAD.replace("\"endLine\": 13", "\"endLine\": 11"), "results[0].source.lineRangeValid");
    }

    @Test
    void rejectsLineRangeWithoutFile() {
        assertViolation(PAYLOAD.replaceFirst("\"path\": \"tests/test_calculator.py\",", ""),
                "results[0].source.lineRangeValid");
    }

    @Test
    void rejectsUnknownStatus() {
        String json = PAYLOAD.replace("\"status\": \"PASSED\"", "\"status\": \"FLAKY\"");

        assertThrows(JacksonException.class, () -> jsonMapper.readValue(json, TestRunRequest.class));
    }

    @Test
    void acceptsPayloadWithoutOptionalCaptureFields() {
        String minimal = """
                {
                  "assignmentKey": "assignment-101",
                  "ide": "VSCODE",
                  "results": [
                    { "testName": "test_add", "status": "FAILED", "source": {} }
                  ]
                }
                """;
        TestRunRequest request = jsonMapper.readValue(minimal, TestRunRequest.class);

        assertNull(request.results().getFirst().source().path());
        assertEquals(List.of(), request.testFiles());
        assertNull(request.captureDisabled());

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<TestRunRequest>> violations = factory.getValidator().validate(request);
            assertTrue(violations.isEmpty(), () -> "unexpected violations: " + violations);
        }
    }

    private static String withExtraFile(String json, int contentChars) {
        return json.replace("\"testFiles\": [",
                "\"testFiles\": [ { \"path\": \"src/big.py\", \"content\": \"" + "x".repeat(contentChars) + "\" },");
    }

    private void assertViolation(String json, String path) {
        assertViolations(json, Set.of(path));
    }

    private void assertViolations(String json, Set<String> expectedPaths) {
        TestRunRequest request = jsonMapper.readValue(json, TestRunRequest.class);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<String> paths = factory.getValidator().validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());
            assertEquals(expectedPaths, paths);
        }
    }
}
