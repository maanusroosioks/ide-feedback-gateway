package com.example.idefeedbackgateway.api.dto;

import java.util.List;

public record ApiErrorResponse(
        String errorMessage,
        ApiErrorCode errorCode,
        List<String> details
) {
    public static ApiErrorResponse of(String errorMessage, ApiErrorCode errorCode) {
        return new ApiErrorResponse(errorMessage, errorCode, List.of());
    }

    public static ApiErrorResponse of(String errorMessage, ApiErrorCode errorCode, List<String> details) {
        return new ApiErrorResponse(errorMessage, errorCode, details);
    }
}
