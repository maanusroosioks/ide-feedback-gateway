package com.example.idefeedbackgateway.security;

import com.example.idefeedbackgateway.security.config.GraphProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class GraphOpaqueTokenIntrospectorTest {

    private static final String BASE_URL = "https://graph.example.test/v1.0";
    private static final String ORGANIZATION_URI = BASE_URL + "/organization?$select=id,displayName";
    private static final String ME_URI = BASE_URL + "/me";
    private static final String TOKEN = "graph-access-token";
    private static final String TALTECH_TENANT = "11111111-2222-3333-4444-555555555555";
    private static final String OTHER_TENANT = "99999999-8888-7777-6666-555555555555";

    private MockRestServiceServer server;
    private GraphOpaqueTokenIntrospector introspector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        introspector = new GraphOpaqueTokenIntrospector(builder.build(), properties());
    }

    @Test
    void acceptsAMemberOfTheExpectedTenant() {
        expectOrganization(TALTECH_TENANT);
        expectMe("{\"id\":\"aaaa-bbbb\",\"userPrincipalName\":\"student@taltech.ee\","
                + "\"mail\":\"s.tudent@taltech.ee\",\"displayName\":\"Some Student\"}");

        OAuth2AuthenticatedPrincipal principal = introspector.introspect(TOKEN);

        assertEquals("student@taltech.ee", principal.getName());
        assertEquals("s.tudent@taltech.ee", principal.getAttribute("mail"));
        assertEquals("aaaa-bbbb", principal.getAttribute("id"));
        assertEquals("Some Student", principal.getAttribute("displayName"));
        server.verify();
    }

    @Test
    void rejectsATokenFromAnotherTenant() {
        expectOrganization(OTHER_TENANT);

        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect(TOKEN),
                "a personal or foreign-tenant account must not authenticate");
        server.verify();
    }

    @Test
    void rejectsGuestAccountsOfTheExpectedTenant() {
        expectOrganization(TALTECH_TENANT);
        expectMe("{\"userPrincipalName\":\"alice_other.com#EXT#@taltech.onmicrosoft.com\","
                + "\"mail\":\"alice@other.com\"}");

        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect(TOKEN),
                "a guest UPN is not an identity Moodle can match");
        server.verify();
    }

    @Test
    void rejectsTokenGraphDoesNotAccept() {
        server.expect(requestTo(ORGANIZATION_URI)).andRespond(withUnauthorizedRequest());

        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect(TOKEN));
        server.verify();
    }

    /**
     * Drives the 401-vs-503 split: only BadOpaqueTokenException means the caller is at fault.
     */
    @Test
    void reportsGraphOutageAsAProviderFailureRatherThanABadToken() {
        server.expect(requestTo(ORGANIZATION_URI)).andRespond(withServerError());

        OAuth2IntrospectionException thrown =
                assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect(TOKEN));

        assertFalse(thrown instanceof BadOpaqueTokenException,
                "a Graph outage must not be reported to the caller as an invalid token");
        server.verify();
    }

    /**
     * A 403 means this gateway lacks permission for /organization, not that the caller is bad.
     * Treating it as a bad token would lock out everyone and hide the cause.
     */
    @Test
    void reportsMissingPermissionAsAProviderFailureRatherThanABadToken() {
        server.expect(requestTo(ORGANIZATION_URI)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        OAuth2IntrospectionException thrown =
                assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect(TOKEN));

        assertFalse(thrown instanceof BadOpaqueTokenException);
        server.verify();
    }

    @Test
    void reportsUnreachableGraphAsAProviderFailureRatherThanABadToken() {
        server.expect(requestTo(ORGANIZATION_URI)).andRespond(withException(new IOException("connect timed out")));

        OAuth2IntrospectionException thrown =
                assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect(TOKEN));

        assertFalse(thrown instanceof BadOpaqueTokenException);
        server.verify();
    }

    @Test
    void reportsMissingPermissionOnTheUserLookupAsAProviderFailure() {
        expectOrganization(TALTECH_TENANT);
        server.expect(requestTo(ME_URI)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        OAuth2IntrospectionException thrown =
                assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect(TOKEN));

        assertFalse(thrown instanceof BadOpaqueTokenException);
        server.verify();
    }

    @Test
    void rejectsATokenForWhichGraphReturnsNoOrganization() {
        server.expect(requestTo(ORGANIZATION_URI))
                .andRespond(withSuccess("{\"value\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect(TOKEN),
                "a personal Microsoft account has no organization");
        server.verify();
    }

    @Test
    void rejectsAUserWithoutAUserPrincipalName() {
        expectOrganization(TALTECH_TENANT);
        expectMe("{\"id\":\"aaaa-bbbb\",\"mail\":\"student@taltech.ee\"}");

        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect(TOKEN),
                "the UPN is the identity passed to Moodle");
        server.verify();
    }

    @Test
    void matchesTheTenantIdIgnoringCase() {
        expectOrganization(TALTECH_TENANT.toUpperCase());
        expectMe("{\"userPrincipalName\":\"student@taltech.ee\"}");

        assertEquals("student@taltech.ee", introspector.introspect(TOKEN).getName());
        server.verify();
    }

    @Test
    void cachesSuccessfulLookupsSoGraphIsNotCalledPerRequest() {
        expectOrganization(TALTECH_TENANT);
        expectMe("{\"userPrincipalName\":\"student@taltech.ee\"}");

        assertEquals("student@taltech.ee", introspector.introspect(TOKEN).getName());
        assertEquals("student@taltech.ee", introspector.introspect(TOKEN).getName());

        server.verify();
    }

    /** A rejection must not stick: the next attempt with the same token asks Graph again. */
    @Test
    void doesNotCacheRejections() {
        server.expect(ExpectedCount.twice(), requestTo(ORGANIZATION_URI))
                .andRespond(withUnauthorizedRequest());

        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect(TOKEN));
        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect(TOKEN));

        server.verify();
    }

    /** Another caller's token must never be answered with a cached identity. */
    @Test
    void keysTheCacheByToken() {
        expectOrganization(TALTECH_TENANT);
        expectMe("{\"userPrincipalName\":\"student@taltech.ee\"}");
        server.expect(requestTo(ORGANIZATION_URI))
                .andExpect(header("Authorization", "Bearer other-token"))
                .andRespond(withUnauthorizedRequest());

        introspector.introspect(TOKEN);
        assertThrows(BadOpaqueTokenException.class, () -> introspector.introspect("other-token"));

        server.verify();
    }

    private void expectOrganization(String tenantId) {
        server.expect(requestTo(ORGANIZATION_URI))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        "{\"value\":[{\"id\":\"" + tenantId + "\",\"displayName\":\"Test Org\"}]}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectMe(String body) {
        server.expect(requestTo(ME_URI))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private GraphProperties properties() {
        return new GraphProperties(BASE_URL, TALTECH_TENANT, Duration.ofMinutes(5), 1000,
                Duration.ofSeconds(3), Duration.ofSeconds(5));
    }
}
