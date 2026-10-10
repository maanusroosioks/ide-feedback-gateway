package com.example.idefeedbackgateway.api;

import com.example.idefeedbackgateway.api.dto.ApiErrorCode;
import com.example.idefeedbackgateway.api.dto.ApiErrorResponse;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleIntegrationException;
import com.example.idefeedbackgateway.integration.moodle.exception.MoodleValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, request, ex,
                ApiErrorResponse.of("Validation failed", ApiErrorCode.VALIDATION_ERROR, details));
    }

    @ExceptionHandler(MoodleValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMoodleValidation(MoodleValidationException ex,
                                                                   HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, request, ex,
                ApiErrorResponse.of(ex.getMessage(), ApiErrorCode.MOODLE_VALIDATION_ERROR));
    }

    @ExceptionHandler(MoodleIntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleMoodleFailure(MoodleIntegrationException ex,
                                                                HttpServletRequest request) {
        List<String> details = ex.errorCode() != null ? List.of("moodleErrorCode: " + ex.errorCode()) : List.of();
        return respond(HttpStatus.BAD_GATEWAY, request, ex,
                ApiErrorResponse.of("Moodle could not accept the run", ApiErrorCode.MOODLE_UNAVAILABLE, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(HttpMessageNotReadableException ex,
                                                                   HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, request, ex,
                ApiErrorResponse.of("Malformed request body", ApiErrorCode.MALFORMED_REQUEST, invalidFieldDetails(ex)));
    }

    private List<String> invalidFieldDetails(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof InvalidFormatException invalidFormat) || invalidFormat.getPath().isEmpty()) {
            return List.of();
        }
        String field = fieldPath(invalidFormat.getPath());
        if (field.isEmpty()) {
            return List.of();
        }
        Class<?> targetType = invalidFormat.getTargetType();
        String reason = targetType != null && targetType.isEnum()
                ? "must be one of " + Arrays.toString(targetType.getEnumConstants())
                : "is not a valid value";
        return List.of(field + ": " + reason);
    }

    /** Spelled like Bean Validation's paths, e.g. {@code results[0].status}, so both kinds of error read alike. */
    private String fieldPath(List<JacksonException.Reference> path) {
        StringBuilder field = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            if (reference.getPropertyName() != null) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                field.append('[').append(reference.getIndex()).append(']');
            }
        }
        return field.toString();
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, HttpServletRequest request, Exception ex,
                                                     ApiErrorResponse body) {
        log.warn("API {} {} -> {} error={}", request.getMethod(), request.getRequestURI(), status.value(), ex.toString());
        return ResponseEntity.status(status).body(body);
    }
}
