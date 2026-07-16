package com.example.idefeedbackgateway.integration.moodle;

public class MoodleIntegrationException extends RuntimeException {

    public MoodleIntegrationException(String message) {
        super(message);
    }

    public MoodleIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
