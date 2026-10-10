package com.example.idefeedbackgateway.security;

import com.example.idefeedbackgateway.security.config.GraphProperties;
import com.example.idefeedbackgateway.security.dto.GraphMe;
import com.example.idefeedbackgateway.security.dto.GraphOrganizations;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class GraphOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private static final String ME_ENDPOINT = "/me";
    private static final String ORGANIZATION_ENDPOINT = "/organization?$select=id,displayName";
    private static final String GUEST_UPN_MARKER = "#EXT#";
    private static final ExecutorService GRAPH_LOOKUPS = Executors.newVirtualThreadPerTaskExecutor();

    private final RestClient graphRestClient;
    private final GraphProperties properties;
    private final ObjectMapper objectMapper;
    private final Cache<String, Lookup> cache;

    public GraphOpaqueTokenIntrospector(RestClient graphRestClient, GraphProperties properties,
                                        ObjectMapper objectMapper) {
        this.graphRestClient = graphRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cache = Caffeine.newBuilder()
                .expireAfter(Expiry.<String, Lookup>creating((key, lookup) -> lookup.cacheTtl()))
                .maximumSize(properties.cacheMaxSize())
                .build();
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        return cache.get(cacheKey(token), key -> lookUp(token)).principal();
    }

    /** Both Graph calls run at once; a cache miss would otherwise pay for two round trips in a row. */
    private Lookup lookUp(String token) {
        CompletableFuture<GraphMe> identity =
                CompletableFuture.supplyAsync(() -> requireIdentity(token), GRAPH_LOOKUPS);
        try {
            requireExpectedTenant(token);
        } catch (RuntimeException e) {
            identity.exceptionally(ignored -> null).join();
            throw e;
        }
        return new Lookup(principal(join(identity)), cacheTtl(token));
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
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new BadOpaqueTokenException("Graph did not accept the presented token", e);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new OAuth2IntrospectionException(
                    "Graph denied " + what + "; the gateway may need additional permission", e);
        } catch (HttpClientErrorException e) {
            throw new OAuth2IntrospectionException("Graph refused " + what + ": " + e.getStatusCode(), e);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new OAuth2IntrospectionException("Microsoft Graph could not be reached", e);
        }
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private OAuth2AuthenticatedPrincipal principal(GraphMe me) {
        Map<String, Object> attributes = new HashMap<>();
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

    /** A cached lookup must not outlive the token it vouches for. */
    private Duration cacheTtl(String token) {
        Instant expiresAt = tokenExpiry(token);
        if (expiresAt == null) {
            return properties.cacheTtl();
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative()) {
            return Duration.ZERO;
        }
        return remaining.compareTo(properties.cacheTtl()) < 0 ? remaining : properties.cacheTtl();
    }

    private Instant tokenExpiry(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            JsonNode exp = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1])).path("exp");
            return exp.isIntegralNumber() ? Instant.ofEpochSecond(exp.longValue()) : null;
        } catch (IllegalArgumentException | JacksonException e) {
            return null;
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

    private record Lookup(OAuth2AuthenticatedPrincipal principal, Duration cacheTtl) {
    }
}
