package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestRunResponse;
import com.example.idefeedbackgateway.service.IdeTestFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/idetestfeedback")
public class IdeTestFeedbackController {

    private final IdeTestFeedbackService ideTestFeedbackService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/runs")
    public TestRunResponse submitRun(@Valid @RequestBody TestRunRequest request, @AuthenticationPrincipal String userEmail) {
        return ideTestFeedbackService.submitRun(request, userEmail);
    }
}
