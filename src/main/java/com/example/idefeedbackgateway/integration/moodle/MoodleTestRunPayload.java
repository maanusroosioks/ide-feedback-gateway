package com.example.idefeedbackgateway.integration.moodle;

import java.util.List;

public record MoodleTestRunPayload(
        Long idetestfeedbackid,
        Long userid,
        String ide,
        String projectname,
        String commithash,
        Long startedat,
        Long finishedat,
        String status,
        Integer passedcount,
        Integer failedcount,
        Integer skippedcount,
        Integer errorcount,
        List<MoodleTestResultPayload> results
) {
}
