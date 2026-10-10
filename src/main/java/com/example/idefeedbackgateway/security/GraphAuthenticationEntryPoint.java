package com.example.idefeedbackgateway.security;

import com.example.idefeedbackgateway.api.dto.ApiErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GraphAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ApiSecurityErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException, ServletException {

        if (ex instanceof AuthenticationServiceException) {
            log.warn("Rejecting {} {} because the authentication provider failed: {}",
                    request.getMethod(), request.getRequestURI(), ex.toString());
            errorWriter.write(response, HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.AUTH_UNAVAILABLE, "Authentication provider is unavailable");
            return;
        }

        // Sets 401 plus the WWW-Authenticate challenge, then we fill in the usual JSON body.
        bearerEntryPoint.commence(request, response, ex);
        errorWriter.write(response, HttpStatus.valueOf(response.getStatus()),
                ApiErrorCode.UNAUTHORIZED, "A valid Microsoft bearer token is required");
    }
}
