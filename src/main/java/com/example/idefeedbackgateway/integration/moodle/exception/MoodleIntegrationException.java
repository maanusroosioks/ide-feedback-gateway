package com.example.idefeedbackgateway.integration.moodle.exception;

public class MoodleIntegrationException extends RuntimeException {

    private final String errorCode;

    public MoodleIntegrationException(String message) {
        super(message);
        this.errorCode = null;
    }

    public MoodleIntegrationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public MoodleIntegrationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
