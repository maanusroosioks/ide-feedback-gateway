package com.example.idefeedbackgateway.api.dto;

import com.example.idefeedbackgateway.model.Ide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TestRunRequest(
        @NotBlank @Size(max = 100) String assignmentKey,
        @NotNull Ide ide,
        @Size(max = 255) String projectName,
        @Size(max = 100) String commitHash,
        Long startedAt,
        Long finishedAt,
        @NotEmpty List<@Valid TestResultRequest> results
) {
}
