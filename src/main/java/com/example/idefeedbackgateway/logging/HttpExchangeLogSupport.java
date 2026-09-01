package com.example.idefeedbackgateway.logging;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;

public final class HttpExchangeLogSupport {

    private static final int MAX_LOGGED_BODY_CHARS = 10_000;

    private HttpExchangeLogSupport() {
    }

    public static String decode(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    public static void logRequest(Logger log, String source, String method, String uri, String body) {
        log.info("{} REQUEST {} {} body=[{}]", source, method, uri, truncate(body));
    }

    public static void logResponse(Logger log, String source, String method, String uri, int status, long durationMs,
                                   String body) {
        log.atLevel(status >= 400 ? Level.WARN : Level.INFO)
                .log("{} RESPONSE {} {} -> {} ({} ms) body=[{}]", source, method, uri, status, durationMs, truncate(body));
    }

    public static void logTransportFailure(Logger log, String source, String method, String uri, Throwable error) {
        log.error("{} REQUEST {} {} FAILED error={}", source, method, uri, error.toString(), error);
    }

    private static String truncate(String body) {
        return body.length() <= MAX_LOGGED_BODY_CHARS
                ? body
                : body.substring(0, MAX_LOGGED_BODY_CHARS) + "...(truncated)";
    }
}
