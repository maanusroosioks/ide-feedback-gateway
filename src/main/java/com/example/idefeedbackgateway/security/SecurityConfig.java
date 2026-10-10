package com.example.idefeedbackgateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain errorFilterChain(HttpSecurity http) {
        http
                .securityMatcher("/error")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http, OpaqueTokenIntrospector introspector, GraphAuthenticationEntryPoint entryPoint) {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .withObjectPostProcessor(routeAllFailuresTo(entryPoint))
                        .opaqueToken(opaqueToken -> opaqueToken
                                .introspector(introspector)
                                .authenticationConverter(new EmailOpaqueTokenAuthenticationConverter())));
        return http.build();
    }

    private static ObjectPostProcessor<BearerTokenAuthenticationFilter> routeAllFailuresTo(
            GraphAuthenticationEntryPoint entryPoint) {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                AuthenticationEntryPointFailureHandler failureHandler =
                        new AuthenticationEntryPointFailureHandler(entryPoint);
                failureHandler.setRethrowAuthenticationServiceException(false);
                filter.setAuthenticationFailureHandler(failureHandler);
                return filter;
            }
        };
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
