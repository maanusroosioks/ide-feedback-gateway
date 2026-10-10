package com.example.idefeedbackgateway.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenAuthenticationConverter;

/**
 * Exposes the caller's UPN as a plain {@code String} principal so downstream code keeps working
 * with {@code @AuthenticationPrincipal String userEmail}.
 */
class EmailOpaqueTokenAuthenticationConverter implements OpaqueTokenAuthenticationConverter {

    @Override
    public Authentication convert(String introspectedToken, OAuth2AuthenticatedPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal.getName(), null, principal.getAuthorities());
    }
}
