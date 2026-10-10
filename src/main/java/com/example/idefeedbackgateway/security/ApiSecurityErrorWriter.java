package com.example.idefeedbackgateway.security;

import com.example.idefeedbackgateway.api.dto.ApiErrorCode;
import com.example.idefeedbackgateway.api.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security failures are raised inside the filter chain, where {@code @RestControllerAdvice} never
 * runs, so the error body has to be written by hand to keep the shape consistent with the API.
 */
@Component
@RequiredArgsConstructor
public class ApiSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, ApiErrorCode errorCode, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(message, errorCode));
    }
}
