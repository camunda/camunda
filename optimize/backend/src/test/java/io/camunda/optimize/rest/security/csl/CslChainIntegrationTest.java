/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.rest.security.ccsm.CCSMSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.cloud.CCSaaSSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.security.UserIdMigrationService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.security.spring.filter.OAuth2RefreshTokenFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Chain-level integration tests for Optimize's CSL adoption (ADR-0038), for both CCSM and CCSaaS
 * editions with {@code optimize.security.csl.enabled=true}.
 *
 * <p>{@link CslSecurityChainSelectionTest} proves which beans get registered, using a non-web,
 * lazily-initialized {@code ApplicationContextRunner} — deliberately cheap, but it never builds a
 * real {@code SecurityFilterChain}. This class uses a {@link WebApplicationContextRunner} instead,
 * which is web-aware and eagerly initialized, so CSL's real chains get built, and drives them
 * through Spring Security's own {@code springSecurityFilterChain} bean (not a hand-assembled proxy,
 * which would exercise bean-registration order rather than each chain's real {@code @Order}) to
 * prove the actual request-level behaviour: session auth on the webapp path, bearer auth on the API
 * path, permit-all on the unprotected path, the session-or-bearer model on the API chain, the
 * isolation that keeps a bearer token from authenticating on the webapp chain (ADR-0023), and CSRF
 * enforcement/exemption.
 *
 * <p>No Testcontainers, no Elasticsearch/OpenSearch, no real IdP: bearer tokens are signed against
 * an in-JVM JWKS server (mirrors {@code PhysicalTenantApiChainIsolationIT}), and a session is
 * minted directly through the same {@link MapSessionRepository} CSL's {@link
 * SessionRepositoryFilter} uses by default, then presented as a {@code SESSION} cookie — CSL's
 * session handling is Spring Session-backed, not the raw servlet {@code HttpSession}.
 *
 * <p>Pinned to a single thread: {@link #SESSION_STORE} and the shared {@link JwksTestServer} are
 * unguarded mutable state, safe only because this module does not run tests in parallel.
 */
@Execution(ExecutionMode.SAME_THREAD)
class CslChainIntegrationTest {

  private static final String PUBLIC_API_AUDIENCE = "optimize-public-api";
  private static final Map<String, Session> SESSION_STORE = new ConcurrentHashMap<>();
  private static final MapSessionRepository SESSION_REPO = new MapSessionRepository(SESSION_STORE);
  private static JwksTestServer server;

  @BeforeAll
  static void startServer() throws Exception {
    server = JwksTestServer.start("chain-it-key");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  // -------------------------------------------------------------------------
  // CCSM
  // -------------------------------------------------------------------------

  @Test
  void shouldBuildRealSecurityFilterChainsForCcsm() {
    assertChainsAreBuilt(ccsmRunner());
  }

  @Test
  void shouldAuthenticateSessionOnWebappPathForCcsm() {
    assertSessionAuthenticatesWebappPath(ccsmRunner());
  }

  @Test
  void shouldRejectUnauthenticatedOnWebappPathForCcsm() {
    assertUnauthenticatedRejectedOnWebappPath(ccsmRunner());
  }

  @Test
  void shouldRejectInvalidSessionCookieOnWebappPathForCcsm() {
    assertInvalidSessionCookieRejectedOnWebappPath(ccsmRunner());
  }

  @Test
  void shouldAuthenticateBearerTokenOnApiPathForCcsm() throws Exception {
    assertBearerAuthenticatesApiPath(ccsmRunner());
  }

  @Test
  void shouldRejectUnauthenticatedOnApiPathForCcsm() {
    assertUnauthenticatedRejectedOnApiPath(ccsmRunner());
  }

  @Test
  void shouldRejectInvalidSessionCookieOnApiPathForCcsm() {
    assertInvalidSessionCookieRejectedOnApiPath(ccsmRunner());
  }

  @Test
  void shouldAuthenticateSessionOnApiPathForCcsm() {
    assertSessionAuthenticatesApiPath(ccsmRunner());
  }

  @Test
  void shouldPermitAllOnUnprotectedPathForCcsm() {
    assertUnprotectedPathPermitsAll(ccsmRunner());
  }

  @Test
  void shouldRejectBearerTokenOnWebappPathForCcsm() throws Exception {
    assertBearerTokenRejectedOnWebappPath(ccsmRunner());
  }

  @Test
  void shouldEnforceCsrfOnWebappStateChangeForCcsm() {
    assertCsrfEnforcedOnWebappPath(ccsmRunner());
  }

  @Test
  void shouldSucceedWithValidCsrfTokenOnWebappStateChangeForCcsm() {
    assertValidCsrfTokenSucceedsOnWebappPath(ccsmRunner());
  }

  @Test
  void shouldExemptExternalPathFromCsrfForCcsm() {
    assertExternalPathExemptFromCsrf(ccsmRunner());
  }

  // -------------------------------------------------------------------------
  // Bug A: session user whose access token fails the Identity write:* check must be denied,
  // not silently kept authenticated via the id_token fallback
  // (OptimizeCcsmSessionPermissionEnforcementFilter).
  // -------------------------------------------------------------------------

  @Test
  void shouldRejectSessionOnWebappPathWhenAccessTokenNoLongerAuthorizedForCcsm() {
    ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceDenyingSessionAccessToken)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
              request.setCookies(authenticatedSessionCookie());
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              // Without OptimizeCcsmSessionPermissionEnforcementFilter this would incorrectly
              // return 200: CSL's OidcUserAuthenticationConverter#decodeAccessToken swallows the
              // JwtValidationException OptimizeIdentityPermissionValidator throws and falls back
              // to the id_token's claims instead of denying the request.
              //
              // The denial goes through the webapp chain's own AuthenticationEntryPoint, the same
              // one an unauthenticated navigation hits (assertUnauthenticatedRejectedOnWebappPath),
              // so the browser lands on the login instead of an empty 401 page.
              assertThat(response.getStatus())
                  .as(
                      "session with a no-longer-authorized access token on webapp path, body: %s",
                      response.getContentAsString())
                  .isEqualTo(302);
              assertThat(response.getHeader("Location")).isEqualTo("/oauth2/authorization/oidc");
              assertThat(downstream.getRequest()).isNull();
            });
  }

  @Test
  void shouldRejectSessionOnApiPathWhenAccessTokenNoLongerAuthorizedForCcsm() {
    ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceDenyingSessionAccessToken)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request =
                  new MockHttpServletRequest("GET", "/api/report/some-id");
              request.setCookies(authenticatedSessionCookie());
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              // Counterpart to the webapp case: the API chain's entry point answers 401 rather
              // than redirecting, so an API client gets a status it can act on.
              assertThat(response.getStatus())
                  .as(
                      "session with a no-longer-authorized access token on API path, body: %s",
                      response.getContentAsString())
                  .isEqualTo(401);
              assertThat(downstream.getRequest()).isNull();
            });
  }

  @Test
  void
      shouldRejectSessionOnWebappPathWithCleanUnauthorizedWhenAccessTokenCannotBeVerifiedForCcsm() {
    // CCSMTokenService#verifyAccessToken throws TokenVerificationException (not
    // NotAuthorizedException) directly for an invalid/expired token. Without the filter's broader
    // catch this would propagate as an uncaught 500 instead of the clean denial this filter exists
    // to provide.
    ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceWithUnverifiableSessionAccessToken)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
              request.setCookies(authenticatedSessionCookie());
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              assertThat(response.getStatus())
                  .as(
                      "session with an unverifiable access token on webapp path, body: %s",
                      response.getContentAsString())
                  .isEqualTo(302);
              assertThat(downstream.getRequest()).isNull();
            });
  }

  @Test
  void shouldEnforcePermissionAfterTheAccessTokenRefreshForCcsm() {
    // The permission check must not run before CSL's OAuth2RefreshTokenFilter: a session whose
    // access token merely expired has to be refreshed first, so the webapp chain re-checks the
    // permission on a fresh token the way the legacy CCSMAuthenticationCookieFilter did. Asserted
    // on the filter positions because ordering is what makes the difference, and the refresh
    // itself needs a real IdP token endpoint to observe end to end.
    ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceGrantingSessionAccessToken)
        .run(
            ctx -> {
              // given
              final SecurityFilterChain webappChain =
                  (SecurityFilterChain) ctx.getBean("oidcWebappSecurityFilterChain");

              // when
              final var filterTypes =
                  webappChain.getFilters().stream().map(filter -> filter.getClass().getName());

              // then
              assertThat(filterTypes)
                  .containsSubsequence(
                      OAuth2RefreshTokenFilter.class.getName(),
                      OptimizeCcsmSessionPermissionEnforcementFilter.class.getName());
            });
  }

  @Test
  void shouldAllowSessionOnWebappPathWhenAccessTokenStillAuthorizedForCcsm() {
    // Positive control for the two tests above: a session whose access token still passes
    // verifyAccessToken must not be rejected by OptimizeCcsmSessionPermissionEnforcementFilter.
    ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceGrantingSessionAccessToken)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
              request.setCookies(authenticatedSessionCookie());
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              assertThat(response.getStatus())
                  .as(
                      "session with a still-authorized access token on webapp path, body: %s",
                      response.getContentAsString())
                  .isEqualTo(200);
              assertThat(downstream.getRequest()).isNotNull();
            });
  }

  // -------------------------------------------------------------------------
  // Bug B: /api/public/** and /api/ingestion/variable must stay audience-only (no Identity
  // write:* gate), while an ordinary internal API path stays gated (OptimizeCcsmPublicApi
  // carve-out).
  // -------------------------------------------------------------------------

  @Test
  void shouldAllowBearerTokenLackingOptimizePermissionOnPublicApiPathForCcsm() {
    // The token carries the configured api.audience, which this chain requires the way the legacy
    // decoder did, and nothing else.
    final String token = signToken(Instant.now().plusSeconds(60), PUBLIC_API_AUDIENCE);
    ccsmRunner(
            CslChainIntegrationTest::mockCcsmTokenServiceDenyingEveryAccessToken,
            CslChainIntegrationTest::ccsmConfigurationWithPublicApiAudience)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request =
                  new MockHttpServletRequest("GET", "/api/public/some-resource");
              request.addHeader("Authorization", "Bearer " + token);
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              // A client-credentials/M2M token with no write:* Identity grant must still be
              // accepted here: legacy CCSM never gated /api/public/** or
              // /api/ingestion/variable through Identity, only checked the audience.
              assertThat(response.getStatus())
                  .as(
                      "bearer token lacking write:* on the public API carve-out, body: %s",
                      response.getContentAsString())
                  .isEqualTo(200);
              assertThat(downstream.getRequest()).isNotNull();
            });
  }

  @Test
  void shouldRejectBearerTokenLackingOptimizePermissionOnInternalApiPathForCcsm() throws Exception {
    // Proves the carve-out is scoped, not a blanket bypass: the same token that is accepted on
    // /api/public/** must still be rejected on an ordinary internal API path.
    final String token = signBearerToken();
    ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceDenyingEveryAccessToken)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request =
                  new MockHttpServletRequest("GET", "/api/report/some-id");
              request.addHeader("Authorization", "Bearer " + token);
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              assertThat(response.getStatus())
                  .as(
                      "bearer token lacking write:* on an internal API path, body: %s",
                      response.getContentAsString())
                  .isEqualTo(401);
              assertThat(downstream.getRequest()).isNull();
            });
  }

  @Test
  void shouldRejectBearerTokenWithoutPublicApiAudienceOnPublicApiPathForCcsm() {
    // The legacy decoder required api.audience here. CSL validates against one merged audience set
    // and accepts a token matching any entry of it, so a token audienced for Identity would
    // otherwise pass on this chain, which has no Identity gate to stop it.
    final String token = signToken(Instant.now().plusSeconds(60), "optimize-api");
    ccsmRunner(
            CslChainIntegrationTest::mockCcsmTokenServiceDenyingEveryAccessToken,
            CslChainIntegrationTest::ccsmConfigurationWithPublicApiAudience)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request =
                  new MockHttpServletRequest("GET", "/api/public/some-resource");
              request.addHeader("Authorization", "Bearer " + token);
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              assertThat(response.getStatus())
                  .as(
                      "bearer token missing the configured api.audience, body: %s",
                      response.getContentAsString())
                  .isEqualTo(401);
              assertThat(downstream.getRequest()).isNull();
            });
  }

  @Test
  void shouldApplySharedChainSetupOnPublicApiPathForCcsm() {
    // given
    // The carve-out chain is built through CSL's ScopedApiSecurityChainBuilder, so the operator's
    // CORS source, HTTPS-redirect customizers, CSRF configuration and secure headers keep applying
    // to it. A hand-rolled chain silently dropped all of them for these two paths only. CSL's
    // property-driven Content-Security-Policy is the marker asserted here, because Spring Security
    // sets no CSP header on its own, unlike the frame and content-type headers.
    final String token = signToken(Instant.now().plusSeconds(60), PUBLIC_API_AUDIENCE);

    // when
    ccsmRunner(
            CslChainIntegrationTest::mockCcsmTokenServiceDenyingEveryAccessToken,
            CslChainIntegrationTest::ccsmConfigurationWithPublicApiAudience)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request =
                  new MockHttpServletRequest("GET", "/api/public/some-resource");
              request.addHeader("Authorization", "Bearer " + token);
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              // then
              assertThat(response.getHeader("Content-Security-Policy"))
                  .as("CSL's shared chain setup must apply to the public API carve-out")
                  .isNotBlank();
            });
  }

  private static ConfigurationService ccsmConfigurationWithPublicApiAudience() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService.getOptimizeApiConfiguration().setAudience(PUBLIC_API_AUDIENCE);
    return configurationService;
  }

  @Test
  void shouldAllowSessionOnApiPathWhenAccessTokenIsExpiredForCcsm() {
    // Only CSL's webapp chain installs OAuth2RefreshTokenFilter, its API chain restores the
    // session but never refreshes the token. Verifying an expired token on the API chain would
    // reject every /api/** call for the rest of the session, while without this filter such a
    // session keeps working through CSL's id_token fallback. So an expired token is skipped and the
    // next webapp request refreshes it.
    ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceWithExpiredSessionAccessToken)
        .run(
            ctx -> {
              final Filter proxy = resolveSecurityFilter(ctx);
              final MockHttpServletRequest request =
                  new MockHttpServletRequest("GET", "/api/report/some-id");
              request.setCookies(authenticatedSessionCookie());
              final MockHttpServletResponse response = new MockHttpServletResponse();
              final MockFilterChain downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              assertThat(response.getStatus())
                  .as(
                      "session with an expired access token on API path, body: %s",
                      response.getContentAsString())
                  .isEqualTo(200);
              assertThat(downstream.getRequest()).isNotNull();
            });
  }

  private static CCSMTokenService mockCcsmTokenServiceWithExpiredSessionAccessToken() {
    final CCSMTokenService service = mock(CCSMTokenService.class);
    final String expiredToken = signToken(Instant.now().minusSeconds(60));
    when(service.getSessionAccessToken(any())).thenReturn(Optional.of(expiredToken));
    // Would deny the request if it was ever verified, which is exactly what must not happen here.
    doThrow(new NotAuthorizedException("no longer authorized"))
        .when(service)
        .verifyAccessToken(expiredToken);
    return service;
  }

  private static CCSMTokenService mockCcsmTokenServiceDenyingSessionAccessToken() {
    final CCSMTokenService service = mock(CCSMTokenService.class);
    when(service.getSessionAccessToken(any())).thenReturn(Optional.of("session-token"));
    doThrow(new NotAuthorizedException("no longer authorized"))
        .when(service)
        .verifyAccessToken("session-token");
    return service;
  }

  private static CCSMTokenService mockCcsmTokenServiceWithUnverifiableSessionAccessToken() {
    // Distinct from mockCcsmTokenServiceDenyingSessionAccessToken:
    // CCSMTokenService#verifyAccessToken
    // (unlike #verifyToken) does not wrap an invalid/expired token into NotAuthorizedException, so
    // this proves OptimizeCcsmSessionPermissionEnforcementFilter also fails closed on the raw
    // TokenVerificationException instead of letting it propagate as an uncaught 500.
    final CCSMTokenService service = mock(CCSMTokenService.class);
    when(service.getSessionAccessToken(any())).thenReturn(Optional.of("session-token"));
    doThrow(new TokenVerificationException("token invalid"))
        .when(service)
        .verifyAccessToken("session-token");
    return service;
  }

  private static CCSMTokenService mockCcsmTokenServiceGrantingSessionAccessToken() {
    final CCSMTokenService service = mock(CCSMTokenService.class);
    when(service.getSessionAccessToken(any())).thenReturn(Optional.of("session-token"));
    // verifyAccessToken("session-token") stays a no-op (granted) by Mockito default.
    return service;
  }

  private static CCSMTokenService mockCcsmTokenServiceDenyingEveryAccessToken() {
    final CCSMTokenService service = mock(CCSMTokenService.class);
    doThrow(new NotAuthorizedException("no longer authorized"))
        .when(service)
        .verifyAccessToken(anyString());
    return service;
  }

  // -------------------------------------------------------------------------
  // CCSaaS
  // -------------------------------------------------------------------------

  @Test
  void shouldBuildRealSecurityFilterChainsForCcsaas() {
    assertChainsAreBuilt(ccsaasRunner());
  }

  @Test
  void shouldAuthenticateSessionOnWebappPathForCcsaas() {
    assertSessionAuthenticatesWebappPath(ccsaasRunner());
  }

  @Test
  void shouldRejectUnauthenticatedOnWebappPathForCcsaas() {
    assertUnauthenticatedRejectedOnWebappPath(ccsaasRunner());
  }

  @Test
  void shouldRejectInvalidSessionCookieOnWebappPathForCcsaas() {
    assertInvalidSessionCookieRejectedOnWebappPath(ccsaasRunner());
  }

  @Test
  void shouldAuthenticateBearerTokenOnApiPathForCcsaas() throws Exception {
    assertBearerAuthenticatesApiPath(ccsaasRunner());
  }

  @Test
  void shouldRejectUnauthenticatedOnApiPathForCcsaas() {
    assertUnauthenticatedRejectedOnApiPath(ccsaasRunner());
  }

  @Test
  void shouldRejectInvalidSessionCookieOnApiPathForCcsaas() {
    assertInvalidSessionCookieRejectedOnApiPath(ccsaasRunner());
  }

  @Test
  void shouldAuthenticateSessionOnApiPathForCcsaas() {
    assertSessionAuthenticatesApiPath(ccsaasRunner());
  }

  @Test
  void shouldPermitAllOnUnprotectedPathForCcsaas() {
    assertUnprotectedPathPermitsAll(ccsaasRunner());
  }

  @Test
  void shouldRejectBearerTokenOnWebappPathForCcsaas() throws Exception {
    assertBearerTokenRejectedOnWebappPath(ccsaasRunner());
  }

  @Test
  void shouldEnforceCsrfOnWebappStateChangeForCcsaas() {
    assertCsrfEnforcedOnWebappPath(ccsaasRunner());
  }

  @Test
  void shouldSucceedWithValidCsrfTokenOnWebappStateChangeForCcsaas() {
    assertValidCsrfTokenSucceedsOnWebappPath(ccsaasRunner());
  }

  @Test
  void shouldExemptExternalPathFromCsrfForCcsaas() {
    assertExternalPathExemptFromCsrf(ccsaasRunner());
  }

  // -------------------------------------------------------------------------
  // Shared scenario assertions
  // -------------------------------------------------------------------------

  private void assertChainsAreBuilt(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          final var chains = ctx.getBeansOfType(SecurityFilterChain.class);
          assertThat(chains)
              .as("beans found: %s", chains.keySet())
              .containsKeys(
                  "oidcWebappSecurityFilterChain",
                  "oidcApiSecurityFilterChain",
                  "unprotectedPathsSecurityFilterChain");
        });
  }

  private void assertSessionAuthenticatesWebappPath(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
          request.setCookies(authenticatedSessionCookie());
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          assertThat(response.getStatus())
              .as("session on webapp path, body: %s", response.getContentAsString())
              .isEqualTo(200);
          assertThat(downstream.getRequest()).isNotNull();
        });
  }

  private void assertUnauthenticatedRejectedOnWebappPath(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          // Negative control for assertSessionAuthenticatesWebappPath: an unauthenticated browser
          // navigation redirects to the IdP (OptimizeOidcAuthenticationEntryPoint's navigation
          // branch) rather than reaching downstream — it is never silently permit-all either.
          assertThat(response.getStatus())
              .as("unauthenticated request on webapp path, body: %s", response.getContentAsString())
              .isEqualTo(302);
          // The status alone doesn't prove it redirects to the IdP login rather than somewhere else
          // for the wrong reason; single registered client resolves to its own authorization
          // endpoint (see OptimizeCamundaSecurityConfig#resolveLoginRedirectTarget).
          assertThat(response.getHeader("Location")).isEqualTo("/oauth2/authorization/oidc");
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertInvalidSessionCookieRejectedOnWebappPath(
      final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
          request.setCookies(unknownSessionCookie());
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          // Proves the session filter actually resolves the cookie's id against the repository and
          // fails closed on a miss, rather than the chain accepting any well-formed cookie.
          assertThat(response.getStatus())
              .as("unknown session id on webapp path, body: %s", response.getContentAsString())
              .isEqualTo(302);
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertBearerAuthenticatesApiPath(final WebApplicationContextRunner runner)
      throws Exception {
    final String token = signBearerToken();
    final int sessionCountBefore = SESSION_STORE.size();
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/report/some-id");
          request.addHeader("Authorization", "Bearer " + token);
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          assertThat(response.getStatus())
              .as("bearer token on API path, body: %s", response.getContentAsString())
              .isEqualTo(200);
          assertThat(downstream.getRequest()).isNotNull();
          // SessionCreationPolicy.NEVER (ADR-0038): the API chain restores an existing session's
          // SecurityContext but never originates one of its own for a bearer-only request.
          assertThat(SESSION_STORE)
              .as("a bearer-only request must not create a session")
              .hasSize(sessionCountBefore);
        });
  }

  private void assertUnauthenticatedRejectedOnApiPath(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/report/some-id");
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          // Negative control for
          // assertBearerAuthenticatesApiPath/assertSessionAuthenticatesApiPath:
          // without either, authentication is actually required, not silently permit-all.
          assertThat(response.getStatus())
              .as("unauthenticated request on API path, body: %s", response.getContentAsString())
              .isEqualTo(401);
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertInvalidSessionCookieRejectedOnApiPath(
      final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/report/some-id");
          request.setCookies(unknownSessionCookie());
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          // Same proof as the webapp-path variant, for the session-or-bearer API chain: an unknown
          // session id is not a bearer-less free pass.
          assertThat(response.getStatus())
              .as("unknown session id on API path, body: %s", response.getContentAsString())
              .isEqualTo(401);
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertSessionAuthenticatesApiPath(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/report/some-id");
          request.setCookies(authenticatedSessionCookie());
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          // The session-or-bearer model (ADR-0038): ScopedApiSecurityChainBuilder installs the
          // session repository filter ahead of SecurityContextHolderFilter with
          // SessionCreationPolicy.NEVER, so an existing webapp session authenticates here too,
          // without the API chain ever creating one of its own.
          assertThat(response.getStatus())
              .as("session (no bearer) on API path, body: %s", response.getContentAsString())
              .isEqualTo(200);
          assertThat(downstream.getRequest()).isNotNull();
        });
  }

  private void assertUnprotectedPathPermitsAll(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/readyz");
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          assertThat(response.getStatus())
              .as("unprotected path, body: %s", response.getContentAsString())
              .isEqualTo(200);
          assertThat(downstream.getRequest()).isNotNull();
        });
  }

  private void assertBearerTokenRejectedOnWebappPath(final WebApplicationContextRunner runner)
      throws Exception {
    final String token = signBearerToken();
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          // A path deliberately absent from apiPaths() (see OptimizeSecurityPathAdapter): the OIDC
          // callback stays on the webapp chain even though it is shaped like an API path, which is
          // exactly why OptimizeOidcAuthenticationEntryPoint returns 401 (not a 302 to the IdP) for
          // it — matching the AC's "not authenticated, returns 401" isolation claim precisely.
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/authentication/callback");
          request.addHeader("Authorization", "Bearer " + token);
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          assertThat(response.getStatus())
              .as("bearer token on webapp path, body: %s", response.getContentAsString())
              .isEqualTo(401);
          assertThat(downstream.getRequest())
              .as("bearer token must not reach downstream on the webapp chain")
              .isNull();
        });
  }

  private void assertCsrfEnforcedOnWebappPath(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
          request.setCookies(authenticatedSessionCookie());
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          // The session authenticates fine; only the CSRF token is missing, so Spring's CsrfFilter
          // throws MissingCsrfTokenException (no prior request established one in this session) —
          // routed to 401, distinct from a wrong-value token, which throws
          // InvalidCsrfTokenException
          // and yields 403. Either way, the request is rejected before reaching downstream. The 401
          // alone doesn't distinguish this from an unrelated auth failure, so also pin the body to
          // CsrfFilter's own rejection message.
          assertThat(response.getStatus())
              .as(
                  "state-changing webapp request without a CSRF token, body: %s",
                  response.getContentAsString())
              .isEqualTo(401);
          assertThat(response.getContentAsString())
              .as("rejection must be CSRF-specific, not an unrelated auth failure")
              .containsIgnoringCase("csrf");
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertValidCsrfTokenSucceedsOnWebappPath(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final Cookie sessionCookie = authenticatedSessionCookie();

          // CSL's CSRF setup is a double-submit cookie (CookieCsrfTokenRepository): an
          // authenticated GET both sets the X-CSRF-TOKEN cookie and echoes the same value as a
          // response header (see SecurityFilterChainSupport#csrfTokenResponseHeaderFilter), which
          // is exactly what the real SPA client relies on to CSRF-protect its next request.
          final MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/");
          getRequest.setCookies(sessionCookie);
          final MockHttpServletResponse getResponse = new MockHttpServletResponse();
          proxy.doFilter(getRequest, getResponse, new MockFilterChain());

          assertThat(getResponse.getStatus()).isEqualTo(200);
          final Cookie csrfCookie = getResponse.getCookie("X-CSRF-TOKEN");
          final String csrfHeader = getResponse.getHeader("X-CSRF-TOKEN");
          assertThat(csrfCookie).as("an authenticated GET must issue a CSRF cookie").isNotNull();
          assertThat(csrfHeader).as("an authenticated GET must issue a CSRF header").isNotNull();

          final MockHttpServletRequest postRequest = new MockHttpServletRequest("POST", "/");
          postRequest.setCookies(sessionCookie, csrfCookie);
          postRequest.addHeader("X-CSRF-TOKEN", csrfHeader);
          final MockHttpServletResponse postResponse = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(postRequest, postResponse, downstream);

          assertThat(postResponse.getStatus())
              .as(
                  "state-changing webapp request with a valid CSRF token, body: %s",
                  postResponse.getContentAsString())
              .isEqualTo(200);
          assertThat(downstream.getRequest()).isNotNull();
        });
  }

  private void assertExternalPathExemptFromCsrf(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          final Filter proxy = resolveSecurityFilter(ctx);
          final MockHttpServletRequest request =
              new MockHttpServletRequest("POST", "/external/foo");
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          proxy.doFilter(request, response, downstream);

          assertThat(response.getStatus())
              .as(
                  "/external is unprotected and CSRF-exempt, body: %s",
                  response.getContentAsString())
              .isEqualTo(200);
          assertThat(downstream.getRequest()).isNotNull();
        });
  }

  // -------------------------------------------------------------------------
  // Runner builders
  // -------------------------------------------------------------------------

  private static WebApplicationContextRunner baseRunner() {
    return new WebApplicationContextRunner()
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(SessionRepositoryFilter.class, () -> new SessionRepositoryFilter<>(SESSION_REPO))
        .withPropertyValues(
            "optimize.security.csl.enabled=true",
            "camunda.security.authentication.catch-all-unhandled-paths-enabled=false",
            "camunda.security.authentication.method=oidc",
            "camunda.security.authentication.oidc.client-id=test-client",
            "camunda.security.authentication.oidc.client-secret=test-secret",
            "camunda.security.authentication.oidc.issuer-uri=" + server.issuerUri(),
            "camunda.security.authentication.oidc.authorization-uri="
                + server.issuerUri()
                + "/auth",
            "camunda.security.authentication.oidc.token-uri=" + server.issuerUri() + "/token",
            "camunda.security.authentication.oidc.jwk-set-uri=" + server.issuerUri() + "/jwks");
  }

  private WebApplicationContextRunner ccsmRunner() {
    return ccsmRunner(CslChainIntegrationTest::mockCcsmTokenServiceGrantingAccess);
  }

  private WebApplicationContextRunner ccsmRunner(
      final Supplier<CCSMTokenService> ccsmTokenServiceSupplier) {
    return ccsmRunner(
        ccsmTokenServiceSupplier, ConfigurationServiceBuilder::createDefaultConfiguration);
  }

  private WebApplicationContextRunner ccsmRunner(
      final Supplier<CCSMTokenService> ccsmTokenServiceSupplier,
      final Supplier<ConfigurationService> configurationServiceSupplier) {
    return baseRunner()
        .withPropertyValues("spring.profiles.active=ccsm")
        .withBean(ConfigurationService.class, configurationServiceSupplier::get)
        .withBean(
            CustomPreAuthenticatedAuthenticationProvider.class,
            () -> mock(CustomPreAuthenticatedAuthenticationProvider.class))
        .withBean(SessionService.class, () -> mock(SessionService.class))
        .withBean(AuthCookieService.class, () -> mock(AuthCookieService.class))
        .withBean(CCSMTokenService.class, ccsmTokenServiceSupplier::get)
        .withUserConfiguration(
            CCSMSecurityConfigurerAdapter.class,
            OptimizeCamundaSecurityConfig.class,
            OptimizeCcsmSecurityConfiguration.class);
  }

  /**
   * Default CCSM {@link CCSMTokenService} mock: {@code verifyAccessToken} is a no-op (grants
   * access) unless a test overrides it with {@code doThrow(...)}. This is what previously let this
   * bug slip through unnoticed: {@link OptimizeCcsmSecurityConfiguration} was never registered in
   * {@link #ccsmRunner()} at all, so neither {@link OptimizeIdentityPermissionValidator} nor {@link
   * OptimizeCcsmSessionPermissionEnforcementFilter} were ever exercised by a real chain here.
   */
  private static CCSMTokenService mockCcsmTokenServiceGrantingAccess() {
    return mock(CCSMTokenService.class);
  }

  private WebApplicationContextRunner ccsaasRunner() {
    return baseRunner()
        .withPropertyValues("spring.profiles.active=cloud")
        .withBean(ConfigurationService.class, CslChainIntegrationTest::cloudConfiguration)
        .withBean(
            CustomPreAuthenticatedAuthenticationProvider.class,
            () -> mock(CustomPreAuthenticatedAuthenticationProvider.class))
        .withBean(SessionService.class, () -> mock(SessionService.class))
        .withBean(AuthCookieService.class, () -> mock(AuthCookieService.class))
        .withBean(UserIdMigrationService.class, () -> mock(UserIdMigrationService.class))
        .withUserConfiguration(
            CCSaaSSecurityConfigurerAdapter.class,
            CCSaasAuth0WebSecurityConfig.class,
            OptimizeCamundaSecurityConfig.class);
  }

  private static ConfigurationService cloudConfiguration() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    final var cloudAuthConfiguration =
        configurationService.getAuthConfiguration().getCloudAuthConfiguration();
    cloudAuthConfiguration.setClientId("auth0-client");
    cloudAuthConfiguration.setClientSecret("auth0-secret");
    // OptimizeCloudSecurityConfiguration fails startup on a blank organization/cluster id (CCSaaS
    // access control must not silently fail open), so both must be set for the chain to build.
    cloudAuthConfiguration.setOrganizationId("org-1");
    cloudAuthConfiguration.setClusterId("cluster-1");
    return configurationService;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Resolves Spring Security's own aggregate filter bean rather than hand-assembling a {@link
   * FilterChainProxy} from {@code ctx.getBeansOfType(SecurityFilterChain.class)}: that map is
   * ordered by bean registration, not by each chain's {@code @Order}, so a hand-built proxy could
   * silently exercise a different match order than production. {@code springSecurityFilterChain} is
   * built by {@code WebSecurityConfiguration} from the same {@code @Order}-sorted list Spring
   * Security itself uses, so resolving it directly tests the real wiring and ordering.
   */
  private static Filter resolveSecurityFilter(final ApplicationContext ctx) {
    return ctx.getBean(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME, Filter.class);
  }

  private static Cookie authenticatedSessionCookie() {
    final MapSession session = SESSION_REPO.createSession();
    final var authentication = new TestingAuthenticationToken("alice", null, "ROLE_USER");
    authentication.setAuthenticated(true);
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication));
    SESSION_REPO.save(session);
    return new Cookie(
        "SESSION", Base64.getEncoder().encodeToString(session.getId().getBytes(UTF_8)));
  }

  /** A well-formed {@code SESSION} cookie whose id was never saved to {@link #SESSION_REPO}. */
  private static Cookie unknownSessionCookie() {
    return new Cookie(
        "SESSION", Base64.getEncoder().encodeToString("unknown-session-id".getBytes(UTF_8)));
  }

  private static String signBearerToken() {
    return signToken(Instant.now().plusSeconds(60), null);
  }

  private static String signToken(final Instant expiresAt) {
    return signToken(expiresAt, null);
  }

  private static String signToken(final Instant expiresAt, final String audience) {
    final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(server.kid()).build();
    final var claimsBuilder =
        new JWTClaimsSet.Builder()
            .subject("alice")
            .issuer(server.issuerUri())
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(expiresAt));
    if (audience != null) {
      claimsBuilder.audience(audience);
    }
    final var claims = claimsBuilder.build();
    final var jwt = new SignedJWT(header, claims);
    try {
      jwt.sign(server.signer());
    } catch (final JOSEException e) {
      throw new IllegalStateException("Failed to sign the test token", e);
    }
    return jwt.serialize();
  }

  /** Mirrors the {@code JwksTestServer} pattern from {@code PhysicalTenantApiChainIsolationIT}. */
  private static final class JwksTestServer {

    private final HttpServer httpServer;
    private final String kid;
    private final JWSSigner signer;

    private JwksTestServer(final HttpServer httpServer, final String kid, final JWSSigner signer) {
      this.httpServer = httpServer;
      this.kid = kid;
      this.signer = signer;
    }

    static JwksTestServer start(final String kid) throws Exception {
      final var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      final var pair = generator.generateKeyPair();
      final var jwk =
          new RSAKey.Builder((RSAPublicKey) pair.getPublic())
              .privateKey((RSAPrivateKey) pair.getPrivate())
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .keyID(kid)
              .build();
      final var jwkSetJson = new JWKSet(jwk).toPublicJWKSet().toString();
      final var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      final var base = "http://127.0.0.1:" + httpServer.getAddress().getPort();
      final var discoveryDoc =
          """
          {
            "issuer": "%s",
            "authorization_endpoint": "%s/auth",
            "token_endpoint": "%s/token",
            "jwks_uri": "%s/jwks",
            "response_types_supported": ["code"],
            "subject_types_supported": ["public"],
            "id_token_signing_alg_values_supported": ["RS256"]
          }
          """
              .formatted(base, base, base, base);

      httpServer.createContext(
          "/jwks",
          exchange -> {
            final var body = jwkSetJson.getBytes(UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (exchange) {
              exchange.getResponseBody().write(body);
            }
          });
      httpServer.createContext(
          "/.well-known/openid-configuration",
          exchange -> {
            final var body = discoveryDoc.getBytes(UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (exchange) {
              exchange.getResponseBody().write(body);
            }
          });
      httpServer.start();
      return new JwksTestServer(httpServer, kid, new RSASSASigner(jwk));
    }

    String kid() {
      return kid;
    }

    JWSSigner signer() {
      return signer;
    }

    String issuerUri() {
      return "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    void stop() {
      httpServer.stop(0);
    }
  }
}
