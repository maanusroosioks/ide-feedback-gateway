package com.example.idefeedbackgateway.integration.moodle.exception;

public class MoodleValidationException extends MoodleIntegrationException {

    public MoodleValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static boolean matches(String errorCode, String errorCodePrefix) {
        return errorCode != null && errorCode.startsWith(errorCodePrefix);
    }
}
