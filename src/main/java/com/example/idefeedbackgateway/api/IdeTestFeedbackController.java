package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.TestRunRequest;
import com.example.idefeedbackgateway.api.dto.TestRunResponse;
import com.example.idefeedbackgateway.service.IdeTestFeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/idetestfeedback")
public class IdeTestFeedbackController {

    private final IdeTestFeedbackService ideTestFeedbackService;

    public IdeTestFeedbackController(IdeTestFeedbackService ideTestFeedbackService) {
        this.ideTestFeedbackService = ideTestFeedbackService;
    }

    @PostMapping("/runs")
    public ResponseEntity<TestRunResponse> submitRun(@Valid @RequestBody TestRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideTestFeedbackService.submitRun(request));
    }
}
