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
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.ConcurrentHashMap;
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
