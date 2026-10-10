package com.example.idefeedbackgateway.integration.moodle.exception;

public class MoodleIntegrationException extends RuntimeException {

    private final String errorCode;

    public MoodleIntegrationException(String message) {
        this(null, message, null);
    }

    public MoodleIntegrationException(String message, Throwable cause) {
        this(null, message, cause);
    }

    protected MoodleIntegrationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public static MoodleIntegrationException fromMoodleError(String errorCode, String message) {
        return new MoodleIntegrationException(errorCode, message, null);
    }

    public String errorCode() {
        return errorCode;
    }
}
