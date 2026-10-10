package com.example.idefeedbackgateway;

import com.example.idefeedbackgateway.integration.moodle.config.MoodleProperties;
import com.example.idefeedbackgateway.security.config.GraphProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** A missing setting must stop startup and name the property, not surface later as a NullPointerException. */
class ConfigurationPropertiesValidationTest {

    private static final String[] VALID = {
            "graph.base-url=https://graph.example.test/v1.0",
            "graph.tenant-id=11111111-2222-3333-4444-555555555555",
            "graph.cache-ttl=5m",
            "graph.cache-max-size=1000",
            "graph.connect-timeout=3s",
            "graph.read-timeout=5s",
            "moodle.base-url=https://moodle.example.test",
            "moodle.token=test",
            "moodle.submit-run-function=mod_idetestfeedback_submit_test_run",
            "moodle.validation-error-prefix=validation_",
            "moodle.connect-timeout=5s",
            "moodle.read-timeout=30s",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void startsWithACompleteConfiguration() {
        runner.withPropertyValues(VALID).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsAMissingGraphCacheTtl() {
        runner.withPropertyValues(without("graph.cache-ttl")).run(context -> assertThat(context)
                .getFailure().rootCause().hasMessageContaining("cacheTtl"));
    }

    @Test
    void rejectsAMissingMoodleToken() {
        runner.withPropertyValues(without("moodle.token")).run(context -> assertThat(context)
                .getFailure().rootCause().hasMessageContaining("token"));
    }

    private static String[] without(String property) {
        return Arrays.stream(VALID).filter(entry -> !entry.startsWith(property + "=")).toArray(String[]::new);
    }

    @EnableConfigurationProperties({GraphProperties.class, MoodleProperties.class})
    static class PropertiesConfig {
    }
}
