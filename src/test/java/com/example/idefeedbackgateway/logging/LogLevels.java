package com.example.idefeedbackgateway.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pins a logger's level for one test. The effective level otherwise depends on whether a Spring
 * context has configured Logback earlier in the same JVM.
 */
public final class LogLevels {

    private LogLevels() {
    }

    public static Restore set(String loggerName, Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        Level previous = logger.getLevel();
        logger.setLevel(level);
        return () -> logger.setLevel(previous);
    }

    public interface Restore extends AutoCloseable {
        @Override
        void close();
    }
}
