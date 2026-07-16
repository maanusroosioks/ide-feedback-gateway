package com.example.idefeedbackgateway.api.logging;

import com.example.idefeedbackgateway.logging.HttpExchangeLogSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.example.idefeedbackgateway.api");
    private static final int MAX_CACHED_REQUEST_BODY_BYTES = 10_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, MAX_CACHED_REQUEST_BODY_BYTES);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            log(wrappedRequest, wrappedResponse, System.currentTimeMillis() - startedAt);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void log(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long durationMs) {
        HttpExchangeLogSupport.logExchange(log, request.getMethod(), requestUri(request), response.getStatus(), durationMs,
                HttpExchangeLogSupport.truncate(HttpExchangeLogSupport.decode(request.getContentAsByteArray())),
                HttpExchangeLogSupport.truncate(HttpExchangeLogSupport.decode(response.getContentAsByteArray())));
    }

    private String requestUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query != null ? request.getRequestURI() + "?" + query : request.getRequestURI();
    }
}
