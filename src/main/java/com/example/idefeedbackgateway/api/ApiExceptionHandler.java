package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.ApiErrorCode;
import com.example.idefeedbackgateway.api.dto.ApiErrorResponse;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleIntegrationException;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        logFailure(HttpStatus.BAD_REQUEST, request, ex);
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return ApiErrorResponse.of("Validation failed", ApiErrorCode.VALIDATION_ERROR, details);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MoodleValidationException.class)
    public ApiErrorResponse handleMoodleValidation(MoodleValidationException ex, HttpServletRequest request) {
        logFailure(HttpStatus.BAD_REQUEST, request, ex);
        return ApiErrorResponse.of(ex.getMessage(), ApiErrorCode.MOODLE_VALIDATION_ERROR);
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(MoodleIntegrationException.class)
    public ApiErrorResponse handleMoodleFailure(MoodleIntegrationException ex, HttpServletRequest request) {
        logFailure(HttpStatus.BAD_GATEWAY, request, ex);
        return ApiErrorResponse.of(ex.getMessage(), ApiErrorCode.MOODLE_UNAVAILABLE);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiErrorResponse handleMalformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        logFailure(HttpStatus.BAD_REQUEST, request, ex);
        return ApiErrorResponse.of("Malformed request body", ApiErrorCode.MALFORMED_REQUEST, invalidFieldDetails(ex));
    }

    private List<String> invalidFieldDetails(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof InvalidFormatException invalidFormat) || invalidFormat.getPath().isEmpty()) {
            return List.of();
        }
        String field = invalidFormat.getPath().getFirst().getPropertyName();
        if (field == null) {
            return List.of();
        }
        Class<?> targetType = invalidFormat.getTargetType();
        String reason = targetType != null && targetType.isEnum()
                ? "must be one of " + Arrays.toString(targetType.getEnumConstants())
                : "is not a valid value";
        return List.of(field + ": " + reason);
    }

    private void logFailure(HttpStatus status, HttpServletRequest request, Exception ex) {
        log.warn("API {} {} -> {} error={}", request.getMethod(), request.getRequestURI(), status.value(), ex.toString());
    }
}
