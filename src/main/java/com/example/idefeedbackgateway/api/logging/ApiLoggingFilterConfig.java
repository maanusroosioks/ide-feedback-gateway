package com.example.idefeedbackgateway.api.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ApiLoggingFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiLoggingFilter> apiLoggingFilter() {
        FilterRegistrationBean<ApiLoggingFilter> registration = new FilterRegistrationBean<>(new ApiLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
