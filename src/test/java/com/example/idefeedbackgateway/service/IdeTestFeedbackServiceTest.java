package com.example.idefeedbackgateway.service;

import com.example.idefeedbackgateway.api.dto.TestFile;
import com.example.idefeedbackgateway.api.dto.TestResult;
import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestRunResponse;
import com.example.idefeedbackgateway.api.dto.TestSource;
import com.example.idefeedbackgateway.integration.moodle.MoodleClient;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleSubmitRunResponse;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestFilePayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestResultPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestRunPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestSourcePayload;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleValidationException;
import com.example.idefeedbackgateway.model.Ide;
import com.example.idefeedbackgateway.model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdeTestFeedbackServiceTest {

    private static final String EMAIL = "student@taltech.ee";

    private final MoodleClient moodleClient = mock(MoodleClient.class);
    private final IdeTestFeedbackService service = new IdeTestFeedbackService(moodleClient);

    @BeforeEach
    void moodleAcceptsRuns() {
        when(moodleClient.submitTestRun(any())).thenReturn(new MoodleSubmitRunResponse(42L, "ok"));
    }

    @Test
    void returnsMoodlesRunIdAndStatus() {
        TestRunResponse response = service.submitRun(request(List.of(result(source())), List.of(), false), EMAIL);

        assertEquals(42L, response.runId());
        assertEquals("ok", response.status());
    }

    @Test
    void mapsEveryRunField() {
        TestRunRequest request = new TestRunRequest(
                "assignment-101", Ide.VSCODE, "my-project", "a329d43f", "https://github.com/org/repo", 1000L, 2000L,
                List.of(result(source())), List.of(), false);

        MoodleTestRunPayload payload = submit(request);

        assertEquals(EMAIL, payload.userEmail());
        assertEquals("assignment-101", payload.assignmentKey());
        assertEquals(Ide.VSCODE, payload.ide());
        assertEquals("my-project", payload.projectName());
        assertEquals("a329d43f", payload.commitHash());
        assertEquals("https://github.com/org/repo", payload.repoUrl());
        assertEquals(1000L, payload.startedAtMs());
        assertEquals(2000L, payload.finishedAtMs());
        assertEquals(Boolean.FALSE, payload.captureDisabled());
    }

    @Test
    void mapsResultsAndTheirSource() {
        MoodleTestRunPayload payload = submit(request(List.of(result(source())), List.of(), false));

        assertEquals(new MoodleTestResultPayload(
                        "test_calculator", "test_add", TestResultStatus.FAILED, 12, "assert 4 == 5",
                        new MoodleTestSourcePayload("tests/test_calculator.py", 3, 4)),
                payload.results().getFirst());
    }

    @Test
    void keepsResultWithoutSource() {
        MoodleTestRunPayload payload = submit(request(List.of(result(null)), List.of(), false));

        assertNull(payload.results().getFirst().source());
    }

    @Test
    void keepsResultOrder() {
        TestResult first = new TestResult(null, "first", TestResultStatus.PASSED, null, null, null);
        TestResult second = new TestResult(null, "second", TestResultStatus.ERROR, null, null, null);

        MoodleTestRunPayload payload = submit(request(List.of(first, second), List.of(), false));

        assertEquals(List.of("first", "second"),
                payload.results().stream().map(MoodleTestResultPayload::testName).toList());
    }

    @Test
    void sendsEmptyTestFileListWhenNoneWereSent() {
        MoodleTestRunPayload payload = submit(request(List.of(result(null)), null, false));

        assertTrue(payload.testFiles().isEmpty());
    }

    @Test
    void sendsTestFileContentWhenCaptureIsEnabled() {
        MoodleTestFilePayload file = submitFile(false);

        assertEquals(new MoodleTestFilePayload("tests/test_calculator.py", "import pytest", true), file);
    }

    @Test
    void sendsTestFileContentWhenCaptureFlagIsAbsent() {
        MoodleTestFilePayload file = submitFile(null);

        assertEquals("import pytest", file.content());
    }

    @Test
    void dropsTestFileContentWhenCaptureIsDisabled() {
        MoodleTestFilePayload file = submitFile(true);

        assertEquals(new MoodleTestFilePayload("tests/test_calculator.py", null, null), file);
    }

    @Test
    void propagatesMoodleErrors() {
        MoodleValidationException error = new MoodleValidationException("validation_noresults", "No results");
        when(moodleClient.submitTestRun(any())).thenThrow(error);

        TestRunRequest request = request(List.of(result(null)), List.of(), false);
        assertSame(error, assertThrows(MoodleValidationException.class, () -> service.submitRun(request, EMAIL)));
    }

    private MoodleTestFilePayload submitFile(Boolean captureDisabled) {
        TestFile file = new TestFile("tests/test_calculator.py", "import pytest", true);
        return submit(request(List.of(result(null)), List.of(file), captureDisabled)).testFiles().getFirst();
    }

    private MoodleTestRunPayload submit(TestRunRequest request) {
        service.submitRun(request, EMAIL);
        ArgumentCaptor<MoodleTestRunPayload> payload = ArgumentCaptor.forClass(MoodleTestRunPayload.class);
        verify(moodleClient).submitTestRun(payload.capture());
        return payload.getValue();
    }

    private static TestRunRequest request(List<TestResult> results, List<TestFile> testFiles, Boolean captureDisabled) {
        return new TestRunRequest("assignment-101", Ide.VSCODE, null, null, null, null, null,
                results, testFiles, captureDisabled);
    }

    private static TestResult result(TestSource source) {
        return new TestResult("test_calculator", "test_add", TestResultStatus.FAILED, 12, "assert 4 == 5", source);
    }

    private static TestSource source() {
        return new TestSource("tests/test_calculator.py", 3, 4);
    }
}
