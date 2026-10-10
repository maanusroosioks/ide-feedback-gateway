package com.example.idefeedbackgateway.security;

import com.example.idefeedbackgateway.security.config.GraphProperties;
import com.example.idefeedbackgateway.security.dto.GraphMe;
import com.example.idefeedbackgateway.security.dto.GraphOrganizations;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GraphOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private static final String ME_ENDPOINT =
            "/me";
    private static final String ORGANIZATION_ENDPOINT = "/organization?$select=id,displayName";
    private static final String GUEST_UPN_MARKER = "#EXT#";

    private final RestClient graphRestClient;
    private final GraphProperties properties;
    private final Cache<String, GraphMe> cache;

    public GraphOpaqueTokenIntrospector(RestClient graphRestClient, GraphProperties properties) {
        this.graphRestClient = graphRestClient;
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(properties.cacheMaxSize())
                .build();
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        String cacheKey = cacheKey(token);
        GraphMe cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return principal(cached);
        }

        requireExpectedTenant(token);
        GraphMe me = requireIdentity(token);

        cache.put(cacheKey, me);
        return principal(me);
    }

    private void requireExpectedTenant(String token) {
        GraphOrganizations organizations =
                callGraph(token, ORGANIZATION_ENDPOINT, GraphOrganizations.class, "the organization lookup");

        if (organizations == null || organizations.value() == null || organizations.value().isEmpty()) {
            throw new BadOpaqueTokenException("Graph returned no organization for the presented token");
        }

        String tenantId = organizations.value().getFirst().id();
        if (!properties.tenantId().equalsIgnoreCase(tenantId)) {
            log.warn("Rejected a token issued in tenant {}", tenantId);
            throw new BadOpaqueTokenException("Token belongs to a different tenant");
        }
    }

    private GraphMe requireIdentity(String token) {
        GraphMe me = callGraph(token, ME_ENDPOINT, GraphMe.class, "the user lookup");

        if (me == null || me.userPrincipalName() == null) {
            throw new BadOpaqueTokenException("Graph returned a user without a userPrincipalName");
        }
        if (me.userPrincipalName().contains(GUEST_UPN_MARKER)) {
            log.warn("Rejected a guest account; its UPN is not a usable identity for Moodle");
            throw new BadOpaqueTokenException("Guest accounts are not eligible");
        }
        return me;
    }

    private <T> T callGraph(String token, String uri, Class<T> type, String what) {
        try {
            return graphRestClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(type);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new OAuth2IntrospectionException(
                    "Graph denied " + what + "; the gateway may need additional permission", e);
        } catch (HttpClientErrorException e) {
            throw new BadOpaqueTokenException("Graph did not accept the presented token: " + e.getStatusCode(), e);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new OAuth2IntrospectionException("Microsoft Graph could not be reached", e);
        }
    }

    private OAuth2AuthenticatedPrincipal principal(GraphMe me) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "id", me.id());
        putIfPresent(attributes, "userPrincipalName", me.userPrincipalName());
        putIfPresent(attributes, "mail", me.mail());
        putIfPresent(attributes, "displayName", me.displayName());
        putIfPresent(attributes, "givenName", me.givenName());
        putIfPresent(attributes, "surname", me.surname());
        return new OAuth2IntrospectionAuthenticatedPrincipal(
                me.userPrincipalName(), Map.copyOf(attributes), List.of());
    }

    private void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }

    private String cacheKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
