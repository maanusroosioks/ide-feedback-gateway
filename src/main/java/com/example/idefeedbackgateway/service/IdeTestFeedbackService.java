package com.example.idefeedbackgateway.service;

import com.example.idefeedbackgateway.api.dto.TestFile;
import com.example.idefeedbackgateway.api.dto.TestResult;
import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestRunResponse;
import com.example.idefeedbackgateway.api.dto.TestSource;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleSubmitRunResponse;
import com.example.idefeedbackgateway.integration.moodle.MoodleClient;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestFilePayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestResultPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestRunPayload;
import com.example.idefeedbackgateway.integration.moodle.dto.MoodleTestSourcePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IdeTestFeedbackService {

    private final MoodleClient moodleClient;

    public TestRunResponse submitRun(TestRunRequest request, String userEmail) {
        MoodleSubmitRunResponse moodleResponse = moodleClient.submitTestRun(toPayload(request, userEmail));
        return new TestRunResponse(moodleResponse.runId(), moodleResponse.status());
    }

    private MoodleTestRunPayload toPayload(TestRunRequest request, String userEmail) {
        List<MoodleTestResultPayload> results = request.results().stream()
                .map(this::toPayload)
                .toList();

        List<MoodleTestFilePayload> testFiles = request.testFiles() == null
                ? List.of()
                : request.testFiles().stream().map(this::toPayload).toList();

        return new MoodleTestRunPayload(
                userEmail,
                request.assignmentKey(),
                request.ide(),
                request.projectName(),
                request.commitHash(),
                request.startedAt(),
                request.finishedAt(),
                results,
                testFiles,
                request.captureDisabled(),
                request.warningAcknowledged()
        );
    }

    private MoodleTestResultPayload toPayload(TestResult result) {
        return new MoodleTestResultPayload(
                result.testSuite(),
                result.testName(),
                result.status(),
                result.durationMs(),
                result.message(),
                result.stackTraceHash(),
                toPayload(result.source())
        );
    }

    private MoodleTestSourcePayload toPayload(TestSource source) {
        if (source == null) {
            return null;
        }
        return new MoodleTestSourcePayload(
                source.kind(),
                source.filePath(),
                source.startLine(),
                source.endLine(),
                source.code(),
                source.truncated(),
                source.normalizedCodeHash()
        );
    }

    private MoodleTestFilePayload toPayload(TestFile testFile) {
        return new MoodleTestFilePayload(
                testFile.path(),
                testFile.sha256(),
                testFile.content(),
                testFile.truncated()
        );
    }
}
