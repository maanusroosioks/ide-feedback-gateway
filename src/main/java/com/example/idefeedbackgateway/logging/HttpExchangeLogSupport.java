package com.example.idefeedbackgateway.logging;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;

public final class HttpExchangeLogSupport {

    private static final int MAX_LOGGED_BODY_BYTES = 10_000;

    private HttpExchangeLogSupport() {
    }

    public static String decode(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    public static String truncate(String body) {
        return body.length() <= MAX_LOGGED_BODY_BYTES
                ? body
                : body.substring(0, MAX_LOGGED_BODY_BYTES) + "...(truncated)";
    }

    /** Logs a completed exchange: WARN if the status is an error status, INFO otherwise. */
    public static void logExchange(Logger log, String method, String uri, int status, long durationMs,
                                    String requestBody, String responseBody) {
        log.atLevel(status >= 400 ? Level.WARN : Level.INFO)
                .log("{} {} -> {} ({} ms) requestBody=[{}] responseBody=[{}]",
                        method, uri, status, durationMs, requestBody, responseBody);
    }

    public static void logTransportFailure(Logger log, String method, String uri, String requestBody, Throwable error) {
        log.error("{} {} requestBody=[{}] failed error={}", method, uri, requestBody, error.toString());
    }
}
