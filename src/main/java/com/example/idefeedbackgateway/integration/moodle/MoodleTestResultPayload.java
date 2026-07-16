package com.example.idefeedbackgateway.integration.moodle;

public record MoodleTestResultPayload(
        String testsuite,
        String testname,
        String status,
        Integer durationms,
        String message,
        String stacktracehash
) {
}
