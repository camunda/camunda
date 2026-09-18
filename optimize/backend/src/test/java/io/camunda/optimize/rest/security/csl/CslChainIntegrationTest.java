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
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

  private static final Map<String, Session> SESSION_STORE = new ConcurrentHashMap<>();
  private static final MapSessionRepository SESSION_REPO = new MapSessionRepository(SESSION_STORE);
  private static JwksTestServer server;

  /** Stands in for an edition policy that grants, respectively denies, access to Optimize. */
  private static final OptimizeComponentAccessPolicy GRANT = new FixedPolicy(null);

  private static final OptimizeComponentAccessPolicy DENY =
      new FixedPolicy("user has no Optimize permission");

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
  // Component access
  // -------------------------------------------------------------------------

  @Test
  void shouldDenyWebappNavigationWithoutComponentAccessForCcsm() {
    assertNavigationDeniedWithoutComponentAccess(componentAccessRunner(ccsmRunner(), DENY));
  }

  @Test
  void shouldDenySessionApiCallWithoutComponentAccessForCcsm() {
    assertSessionApiCallDeniedWithoutComponentAccess(componentAccessRunner(ccsmRunner(), DENY));
  }

  @Test
  void shouldServeSessionWithComponentAccessForCcsm() {
    assertSessionServedWithComponentAccess(componentAccessRunner(ccsmRunner(), GRANT));
  }

  @Test
  void shouldServeBearerCallWithoutTheComponentCheckForCcsm() throws Exception {
    assertBearerCallUnaffectedByComponentCheck(componentAccessRunner(ccsmRunner(), DENY));
  }

  @Test
  void shouldPermitUnprotectedPathWithoutComponentAccessForCcsm() {
    assertUnprotectedPathUnaffectedByComponentCheck(componentAccessRunner(ccsmRunner(), DENY));
  }

  @Test
  void shouldDenySessionApiCallWithAnAssetLikeNameForCcsm() {
    assertExportDeniedWithoutComponentAccess(
        componentAccessRunner(ccsmRunner(), DENY), "report.js");
  }

  @Test
  void shouldDenySessionApiCallNamedLikeTheForbiddenPageForCcsm() {
    assertExportDeniedWithoutComponentAccess(
        componentAccessRunner(ccsmRunner(), DENY), "forbidden");
  }

  @Test
  void shouldBindTheSessionRequestForTheComponentCheck() {
    // The CCSM policy reads the session's access token through the current request. CSL attaches
    // the session inside the chain, so the request bound outside of it carries none.
    final AtomicReference<Boolean> sawSession = new AtomicReference<>();
    final OptimizeComponentAccessPolicy recordingPolicy =
        new RecordingPolicy(
            () ->
                sawSession.set(
                    ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                            .getRequest()
                            .getSession(false)
                        != null));

    componentAccessRunner(ccsmRunner(), recordingPolicy)
        .run(
            ctx -> {
              // given
              final MockHttpServletRequest navigation = new MockHttpServletRequest("GET", "/");
              navigation.setCookies(oauth2SessionCookie(ctx));
              final MockHttpServletResponse response = new MockHttpServletResponse();

              // when
              doFilterWithRequestContext(ctx, navigation, response, new MockFilterChain());

              // then
              assertThat(sawSession.get())
                  .as("the component check saw the request that carries the session")
                  .isTrue();
            });
  }

  @Test
  void shouldDenyWebappNavigationWithoutComponentAccessForCcsaas() {
    assertNavigationDeniedWithoutComponentAccess(componentAccessRunner(ccsaasRunner(), DENY));
  }

  @Test
  void shouldDenySessionApiCallWithoutComponentAccessForCcsaas() {
    assertSessionApiCallDeniedWithoutComponentAccess(componentAccessRunner(ccsaasRunner(), DENY));
  }

  @Test
  void shouldServeSessionWithComponentAccessForCcsaas() {
    assertSessionServedWithComponentAccess(componentAccessRunner(ccsaasRunner(), GRANT));
  }

  @Test
  void shouldServeBearerCallWithoutTheComponentCheckForCcsaas() throws Exception {
    assertBearerCallUnaffectedByComponentCheck(componentAccessRunner(ccsaasRunner(), DENY));
  }

  @Test
  void shouldPermitUnprotectedPathWithoutComponentAccessForCcsaas() {
    assertUnprotectedPathUnaffectedByComponentCheck(componentAccessRunner(ccsaasRunner(), DENY));
  }

  @Test
  void shouldDenySessionApiCallWithAnAssetLikeNameForCcsaas() {
    assertExportDeniedWithoutComponentAccess(
        componentAccessRunner(ccsaasRunner(), DENY), "report.js");
  }

  @Test
  void shouldDenySessionApiCallNamedLikeTheForbiddenPageForCcsaas() {
    assertExportDeniedWithoutComponentAccess(
        componentAccessRunner(ccsaasRunner(), DENY), "forbidden");
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

  private void assertNavigationDeniedWithoutComponentAccess(
      final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          // given
          final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
          request.setCookies(oauth2SessionCookie(ctx));
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          // when
          doFilterWithRequestContext(ctx, request, response, downstream);

          // then
          // 403 is what OptimizeErrorController renders as the "no authorization to access
          // Optimize" page, instead of CSL's default redirect to a /forbidden route Optimize
          // does not serve.
          assertThat(response.getStatus())
              .as("navigation without component access, body: %s", response.getContentAsString())
              .isEqualTo(403);
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertSessionApiCallDeniedWithoutComponentAccess(
      final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          // given
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/report/some-id");
          request.setCookies(oauth2SessionCookie(ctx));
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          // when
          doFilterWithRequestContext(ctx, request, response, downstream);

          // then
          // The single page app authenticates its own calls with the session cookie, and those
          // run on the API chain, where CSL does not install the filter by itself.
          assertThat(response.getStatus())
              .as(
                  "session API call without component access, body: %s",
                  response.getContentAsString())
              .isEqualTo(401);
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertSessionServedWithComponentAccess(final WebApplicationContextRunner runner) {
    runner.run(
        ctx -> {
          // given
          final Cookie sessionCookie = oauth2SessionCookie(ctx);
          final MockHttpServletRequest navigation = new MockHttpServletRequest("GET", "/");
          navigation.setCookies(sessionCookie);
          final MockHttpServletResponse navigationResponse = new MockHttpServletResponse();
          final MockFilterChain navigationDownstream = new MockFilterChain();
          final MockHttpServletRequest apiCall =
              new MockHttpServletRequest("GET", "/api/report/some-id");
          apiCall.setCookies(sessionCookie);
          final MockHttpServletResponse apiResponse = new MockHttpServletResponse();
          final MockFilterChain apiDownstream = new MockFilterChain();

          // when
          doFilterWithRequestContext(ctx, navigation, navigationResponse, navigationDownstream);
          doFilterWithRequestContext(ctx, apiCall, apiResponse, apiDownstream);

          // then
          assertThat(navigationResponse.getStatus())
              .as(
                  "navigation with component access, body: %s",
                  navigationResponse.getContentAsString())
              .isEqualTo(200);
          assertThat(navigationDownstream.getRequest()).isNotNull();
          assertThat(apiResponse.getStatus())
              .as(
                  "session API call with component access, body: %s",
                  apiResponse.getContentAsString())
              .isEqualTo(200);
          assertThat(apiDownstream.getRequest()).isNotNull();
        });
  }

  private void assertBearerCallUnaffectedByComponentCheck(final WebApplicationContextRunner runner)
      throws Exception {
    // The component check gates login sessions. A bearer caller stays authorized by the audience
    // check of the API chain, as it is without the check.
    final String token = signBearerToken();
    runner.run(
        ctx -> {
          // given
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/report/some-id");
          request.addHeader("Authorization", "Bearer " + token);
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          // when
          doFilterWithRequestContext(ctx, request, response, downstream);

          // then
          assertThat(response.getStatus())
              .as("bearer call with a denying policy, body: %s", response.getContentAsString())
              .isEqualTo(200);
          assertThat(downstream.getRequest()).isNotNull();
        });
  }

  private void assertExportDeniedWithoutComponentAccess(
      final WebApplicationContextRunner runner, final String fileName) {
    runner.run(
        ctx -> {
          // given
          // The file name of an export is the last path segment and the caller picks it. CSL's own
          // filter skips the check for a URI ending in a static-asset suffix or in /forbidden, so
          // such a name would be a way around it.
          final MockHttpServletRequest request =
              new MockHttpServletRequest("GET", "/api/export/csv/some-id/" + fileName);
          request.setCookies(oauth2SessionCookie(ctx));
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          // when
          doFilterWithRequestContext(ctx, request, response, downstream);

          // then
          assertThat(response.getStatus())
              .as(
                  "export named %s, without component access, body: %s",
                  fileName, response.getContentAsString())
              .isEqualTo(401);
          assertThat(downstream.getRequest()).isNull();
        });
  }

  private void assertUnprotectedPathUnaffectedByComponentCheck(
      final WebApplicationContextRunner runner) {
    // The check runs on every chain, the unprotected one included, so a denied session must still
    // reach a liveness probe.
    runner.run(
        ctx -> {
          // given
          final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/readyz");
          request.setCookies(oauth2SessionCookie(ctx));
          final MockHttpServletResponse response = new MockHttpServletResponse();
          final MockFilterChain downstream = new MockFilterChain();

          // when
          doFilterWithRequestContext(ctx, request, response, downstream);

          // then
          assertThat(response.getStatus())
              .as("unprotected path with a denying policy, body: %s", response.getContentAsString())
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
    return baseRunner()
        .withPropertyValues("spring.profiles.active=ccsm")
        .withBean(
            ConfigurationService.class, ConfigurationServiceBuilder::createDefaultConfiguration)
        .withBean(
            CustomPreAuthenticatedAuthenticationProvider.class,
            () -> mock(CustomPreAuthenticatedAuthenticationProvider.class))
        .withBean(SessionService.class, () -> mock(SessionService.class))
        .withBean(AuthCookieService.class, () -> mock(AuthCookieService.class))
        .withBean(CCSMTokenService.class, () -> mock(CCSMTokenService.class))
        .withUserConfiguration(
            CCSMSecurityConfigurerAdapter.class, OptimizeCamundaSecurityConfig.class);
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

  /**
   * Adds Optimize's component-access ports on top of an edition runner, with a fixed policy in
   * place of the edition's own one. Both editions share every enforcement point, so the policy is
   * the only part that differs, and it is covered by its own unit tests.
   */
  private WebApplicationContextRunner componentAccessRunner(
      final WebApplicationContextRunner runner, final OptimizeComponentAccessPolicy policy) {
    return runner
        .withBean(OptimizeComponentAccessPolicy.class, () -> policy)
        .withUserConfiguration(OptimizeComponentAccessConfiguration.class);
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

  /**
   * A {@code SESSION} cookie for an OIDC login session. {@link OptimizeWebAppProviderAdapter}
   * claims the Optimize component only for such a session, so the component check needs a real
   * {@link OAuth2AuthenticationToken}, unlike the {@link TestingAuthenticationToken} of {@link
   * #authenticatedSessionCookie()}. The session also carries an authorized client with an unexpired
   * access token, because CSL's {@code OAuth2RefreshTokenFilter} logs a session out when it finds
   * none.
   */
  private static Cookie oauth2SessionCookie(final ApplicationContext ctx) {
    final ClientRegistration registration = firstClientRegistration(ctx);
    final Instant now = Instant.now();
    final var idToken =
        new OidcIdToken(
            "id-token", now, now.plusSeconds(300), Map.of("sub", "alice", "iss", "http://idp"));
    final var user =
        new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, "sub");
    final var authentication =
        new OAuth2AuthenticationToken(
            user, user.getAuthorities(), registration.getRegistrationId());
    final var authorizedClient =
        new OAuth2AuthorizedClient(
            registration,
            user.getName(),
            new OAuth2AccessToken(TokenType.BEARER, "access-token", now, now.plusSeconds(300)));

    final MapSession session = SESSION_REPO.createSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication));
    session.setAttribute(
        HttpSessionOAuth2AuthorizedClientRepository.class.getName() + ".AUTHORIZED_CLIENTS",
        new HashMap<>(Map.of(registration.getRegistrationId(), authorizedClient)));
    SESSION_REPO.save(session);
    return new Cookie(
        "SESSION", Base64.getEncoder().encodeToString(session.getId().getBytes(UTF_8)));
  }

  /**
   * The first client registration of the context. The repository resolves its registrations on
   * first use, so the bean type says nothing about how many it holds; every implementation in play
   * here exposes them as an {@link Iterable}.
   */
  @SuppressWarnings("unchecked")
  private static ClientRegistration firstClientRegistration(final ApplicationContext ctx) {
    return ((Iterable<ClientRegistration>) ctx.getBean(ClientRegistrationRepository.class))
        .iterator()
        .next();
  }

  /**
   * Runs the chain with the request bound to {@link RequestContextHolder}. CSL's authentication
   * converter injects the current request, which production binds outside the security chain.
   */
  private static void doFilterWithRequestContext(
      final ApplicationContext ctx,
      final MockHttpServletRequest request,
      final MockHttpServletResponse response,
      final MockFilterChain downstream)
      throws Exception {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    try {
      resolveSecurityFilter(ctx).doFilter(request, response, downstream);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }

  private static String signBearerToken() throws Exception {
    final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(server.kid()).build();
    final var claims =
        new JWTClaimsSet.Builder()
            .subject("alice")
            .issuer(server.issuerUri())
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .build();
    final var jwt = new SignedJWT(header, claims);
    jwt.sign(server.signer());
    return jwt.serialize();
  }

  /** Reports the session as authorized and runs the given probe on every session check. */
  private record RecordingPolicy(Runnable probe) implements OptimizeComponentAccessPolicy {

    @Override
    public Either<String, Void> checkAccess(final CamundaAuthentication authentication) {
      probe.run();
      return Either.right(null);
    }
  }

  private record FixedPolicy(String denialReason) implements OptimizeComponentAccessPolicy {

    @Override
    public Either<String, Void> checkAccess(final CamundaAuthentication authentication) {
      return denialReason == null ? Either.right(null) : Either.left(denialReason);
    }
  }
}
