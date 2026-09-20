package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.TestFile;
import com.example.idefeedbackgateway.api.dto.TestResult;
import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestSource;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.SourceKind;
import com.example.idefeedbackgateway.model.TestResultStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunRequestBindingTest {

    private static final String PAYLOAD = """
            {
              "assignmentKey": "assignment-101",
              "ide": "VSCODE",
              "projectName": "my-project",
              "commitHash": "a329d43f8b1e",
              "startedAt": 1758134400000,
              "finishedAt": 1758134403120,
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
                    "filePath": "tests/test_calculator.py",
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

    @Test
    void bindsFullPayloadIncludingSourceAndTestFiles() {
        TestRunRequest request = jsonMapper.readValue(PAYLOAD, TestRunRequest.class);

        assertEquals("assignment-101", request.assignmentKey());
        assertEquals(Ide.VSCODE, request.ide());
        assertEquals(1758134400000L, request.startedAt());
        assertEquals(Boolean.TRUE, request.captureDisabled());
        assertEquals(Boolean.TRUE, request.warningAcknowledged());

        TestResult result = request.results().getFirst();
        assertEquals(TestResultStatus.PASSED, result.status());

        TestSource source = result.source();
        assertEquals(SourceKind.TEST, source.kind());
        assertEquals("tests/test_calculator.py", source.filePath());
        assertEquals(12, source.startLine());
        assertEquals(13, source.endLine());
        assertEquals(Boolean.FALSE, source.truncated());
        assertEquals("b1946ac92492d234", source.normalizedCodeHash());
        assertTrue(source.code().contains("assert add(2, 3) == 5"));

        TestFile testFile = request.testFiles().getFirst();
        assertEquals("tests/test_calculator.py", testFile.path());
        assertEquals("c7be1ed902fb8dd4", testFile.sha256());
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
    void rejectsUnknownSourceKind() {
        String bad = PAYLOAD.replace("\"kind\": \"TEST\"", "\"kind\": \"SNIPPET\"");
        assertThrowsOnBind(bad);
    }

    @Test
    void acceptsPayloadWithoutOptionalCaptureFields() {
        String minimal = """
                {
                  "assignmentKey": "assignment-101",
                  "ide": "VSCODE",
                  "results": [
                    { "testName": "test_add", "status": "FAILED", "source": { "kind": "NONE" } }
                  ]
                }
                """;
        TestRunRequest request = jsonMapper.readValue(minimal, TestRunRequest.class);

        assertEquals(SourceKind.NONE, request.results().getFirst().source().kind());
        assertEquals(null, request.testFiles());
        assertEquals(null, request.captureDisabled());

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<TestRunRequest>> violations = factory.getValidator().validate(request);
            assertTrue(violations.isEmpty(), () -> "unexpected violations: " + violations);
        }
    }

    private void assertThrowsOnBind(String json) {
        boolean failed;
        try {
            jsonMapper.readValue(json, TestRunRequest.class);
            failed = false;
        } catch (RuntimeException e) {
            failed = true;
        }
        assertFalse(!failed, "expected binding to fail");
    }
}
