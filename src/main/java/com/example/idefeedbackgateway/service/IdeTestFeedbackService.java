package com.example.idefeedbackgateway.service;

import com.example.idefeedbackgateway.api.dto.TestResultRequest;
import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestRunResponse;
import com.example.idefeedbackgateway.integration.moodle.MoodleSubmitRunResponse;
import com.example.idefeedbackgateway.integration.moodle.MoodleTestFeedbackClient;
import com.example.idefeedbackgateway.integration.moodle.MoodleTestResultPayload;
import com.example.idefeedbackgateway.integration.moodle.MoodleTestRunPayload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IdeTestFeedbackService {

    private final MoodleTestFeedbackClient moodleTestFeedbackClient;

    public IdeTestFeedbackService(MoodleTestFeedbackClient moodleTestFeedbackClient) {
        this.moodleTestFeedbackClient = moodleTestFeedbackClient;
    }

    public TestRunResponse submitRun(TestRunRequest request) {
        MoodleSubmitRunResponse moodleResponse = moodleTestFeedbackClient.submitTestRun(toPayload(request));
        return new TestRunResponse(moodleResponse.runId(), moodleResponse.status());
    }

    private MoodleTestRunPayload toPayload(TestRunRequest request) {
        List<MoodleTestResultPayload> results = request.results().stream()
                .map(this::toPayload)
                .toList();

        return new MoodleTestRunPayload(
                request.idetestfeedbackid(),
                request.userid(),
                request.ide(),
                request.projectname(),
                request.commithash(),
                request.startedat(),
                request.finishedat(),
                request.status(),
                request.passedcount(),
                request.failedcount(),
                request.skippedcount(),
                request.errorcount(),
                results
        );
    }

    private MoodleTestResultPayload toPayload(TestResultRequest result) {
        return new MoodleTestResultPayload(
                result.testsuite(),
                result.testname(),
                result.status(),
                result.durationms(),
                result.message(),
                result.stacktracehash()
        );
    }
}
