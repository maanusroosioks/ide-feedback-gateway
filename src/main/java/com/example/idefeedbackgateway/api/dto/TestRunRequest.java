package com.example.idefeedbackgateway.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TestRunRequest(
        @NotNull Long idetestfeedbackid,
        @NotNull Long userid,
        @NotBlank @Size(max = 50) String ide,
        @Size(max = 255) String projectname,
        @Size(max = 100) String commithash,
        Long startedat,
        Long finishedat,
        @NotBlank @Size(max = 30) String status,
        @NotNull @Min(0) Integer passedcount,
        @NotNull @Min(0) Integer failedcount,
        @NotNull @Min(0) Integer skippedcount,
        @NotNull @Min(0) Integer errorcount,
        @NotEmpty @Valid List<TestResultRequest> results
) {
}
