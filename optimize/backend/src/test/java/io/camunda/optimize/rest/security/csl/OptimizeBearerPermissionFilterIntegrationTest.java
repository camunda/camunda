/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.rest.security.ccsm.CCSMSecurityConfigurerAdapter;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.Date;
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
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Chain-level test of {@link OptimizeBearerPermissionFilter}, mirroring {@link
 * CslChainIntegrationTest}'s own pattern: drives signed bearer JWTs through the real {@code
 * springSecurityFilterChain} bean rather than calling the filter directly, so it proves the filter
 * is actually installed at the right point on the real CCSM CSL chains — including {@code
 * /api/public/**} and {@code /api/ingestion/variable}, which is what makes this an integration test
 * rather than a second copy of {@link OptimizeBearerPermissionFilterTest}. Covers camunda/camunda
 * #63372's manual QA matrix cases 11 and 12 (a role-less user's bearer token against an internal
 * API path and a public API path) plus the ingestion carve-out and the M2M-must-not-regress case.
 */
@Execution(ExecutionMode.SAME_THREAD)
class OptimizeBearerPermissionFilterIntegrationTest {

  private static JwksTestServer server;

  @BeforeAll
  static void startServer() throws Exception {
    server = JwksTestServer.start("bearer-permission-it-key");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  private WebApplicationContextRunner ccsmRunner() {
    return new WebApplicationContextRunner()
        .withPropertyValues(
            "optimize.security.csl.enabled=true",
            "spring.profiles.active=ccsm",
            "camunda.security.authentication.catch-all-unhandled-paths-enabled=false",
            "camunda.security.authentication.method=oidc",
            // Normally bridged by OptimizeSecurityConfigCompatibilityPostProcessor, which
            // WebApplicationContextRunner never invokes (it bypasses SpringApplication.run); set
            // explicitly so the classification this filter reads from CamundaAuthenticationProvider
            // matches production behavior for a default (Keycloak) CCSM deployment.
            "camunda.security.authentication.oidc.client-id-claim=client_id",
            "camunda.security.authentication.oidc.client-id=test-client",
            "camunda.security.authentication.oidc.client-secret=test-secret",
            "camunda.security.authentication.oidc.issuer-uri=" + server.issuerUri(),
            "camunda.security.authentication.oidc.authorization-uri="
                + server.issuerUri()
                + "/auth",
            "camunda.security.authentication.oidc.token-uri=" + server.issuerUri() + "/token",
            "camunda.security.authentication.oidc.jwk-set-uri=" + server.issuerUri() + "/jwks")
        // CamundaSecurityAutoConfiguration's AuthFailureHandlerConfiguration wires an ObjectMapper
        // into every OIDC API chain's access-denied handler; WebApplicationContextRunner has no
        // Jackson autoconfiguration of its own, so without this bean the context fails to refresh
        // (the same reason CslChainIntegrationTest's baseRunner() supplies it).
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(
            ConfigurationService.class, ConfigurationServiceBuilder::createDefaultConfiguration)
        .withBean(
            CustomPreAuthenticatedAuthenticationProvider.class,
            () -> mock(CustomPreAuthenticatedAuthenticationProvider.class))
        .withBean(SessionService.class, () -> mock(SessionService.class))
        .withBean(AuthCookieService.class, () -> mock(AuthCookieService.class))
        .withBean(CCSMTokenService.class, () -> mock(CCSMTokenService.class))
        .withUserConfiguration(
            CCSMSecurityConfigurerAdapter.class,
            OptimizeCamundaSecurityConfig.class,
            OptimizeBearerPermissionConfiguration.class);
  }

  private static Filter resolveSecurityFilter(final ApplicationContext ctx) {
    return ctx.getBean(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME, Filter.class);
  }

  private static String signBearerToken(final JWTClaimsSet.Builder claims) throws Exception {
    final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(server.kid()).build();
    final var jwt =
        new SignedJWT(
            header,
            claims
                .issuer(server.issuerUri())
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build());
    jwt.sign(server.signer());
    return jwt.serialize();
  }

  private static String userToken(final String username) throws Exception {
    return signBearerToken(
        new JWTClaimsSet.Builder().subject(username).claim("preferred_username", username));
  }

  private static String m2mToken(final String clientId) throws Exception {
    return signBearerToken(
        new JWTClaimsSet.Builder().subject(clientId).claim("client_id", clientId));
  }

  private MockHttpServletResponse callWithBearerToken(
      final ApplicationContext ctx, final String path, final String token) throws Exception {
    final var request = new MockHttpServletRequest("GET", path);
    request.addHeader("Authorization", "Bearer " + token);
    final var response = new MockHttpServletResponse();
    // A real deployment always dispatches through DispatcherServlet, which binds the request via
    // RequestContextListener/RequestContextFilter. This test drives the security filter chain
    // directly (see resolveSecurityFilter), bypassing that, so CSL's session-based
    // CamundaAuthenticationHolder — consulted for every CamundaAuthenticationProvider lookup,
    // including OptimizeBearerPermissionFilter's — would otherwise fail with "No thread-bound
    // request found".
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    try {
      resolveSecurityFilter(ctx).doFilter(request, response, new MockFilterChain());
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
    return response;
  }

  @Test
  void shouldReject401ForARoleLessUserOnAnInternalApiPath() throws Exception {
    // given: case 11 of the QA matrix — role-less user bearer, GET /api/entities
    final String token = userToken("noopt");
    ccsmRunner()
        .run(
            ctx -> {
              // given
              doThrow(new NotAuthorizedException("User is not authorized to access Optimize"))
                  .when(ctx.getBean(CCSMTokenService.class))
                  .verifyAccessToken(token);

              // when
              final MockHttpServletResponse response =
                  callWithBearerToken(ctx, "/api/entities", token);

              // then
              assertThat(response.getStatus()).isEqualTo(401);
            });
  }

  @Test
  void shouldReject401ForARoleLessUserOnThePublicApiCarveOut() throws Exception {
    // given: case 12 of the QA matrix — role-less user bearer, GET /api/public/collection
    final String token = userToken("noopt");
    ccsmRunner()
        .run(
            ctx -> {
              // given
              doThrow(new NotAuthorizedException("User is not authorized to access Optimize"))
                  .when(ctx.getBean(CCSMTokenService.class))
                  .verifyAccessToken(token);

              // when
              final MockHttpServletResponse response =
                  callWithBearerToken(ctx, "/api/public/collection", token);

              // then
              assertThat(response.getStatus()).isEqualTo(401);
            });
  }

  @Test
  void shouldReject401ForARoleLessUserOnTheIngestionVariableCarveOut() throws Exception {
    // given: the other carve-out the issue calls out as uncovered today
    final String token = userToken("noopt");
    ccsmRunner()
        .run(
            ctx -> {
              // given
              doThrow(new NotAuthorizedException("User is not authorized to access Optimize"))
                  .when(ctx.getBean(CCSMTokenService.class))
                  .verifyAccessToken(token);

              // when
              final MockHttpServletResponse response =
                  callWithBearerToken(ctx, "/api/ingestion/variable", token);

              // then
              assertThat(response.getStatus()).isEqualTo(401);
            });
  }

  @Test
  void shouldNotRegressAnM2mClientWithoutTheOptimizePermission() throws Exception {
    // given: matrix row "M2M client without the Optimize permission, GET /api/entities" — must
    // stay 200, and the token service must never even be consulted for it.
    final String token = m2mToken("optimize-api-client");
    ccsmRunner()
        .run(
            ctx -> {
              // when
              final MockHttpServletResponse response =
                  callWithBearerToken(ctx, "/api/entities", token);

              // then
              assertThat(response.getStatus()).isEqualTo(200);
              verify(ctx.getBean(CCSMTokenService.class), never()).verifyAccessToken(anyString());
            });
  }

  @Test
  void shouldLetARoleLessUserThroughWhenTheTokenCannotBeFreshlyVerified() throws Exception {
    // given
    final String token = userToken("noopt");
    ccsmRunner()
        .run(
            ctx -> {
              // given
              doThrow(new TokenVerificationException("token expired"))
                  .when(ctx.getBean(CCSMTokenService.class))
                  .verifyAccessToken(token);

              // when
              final MockHttpServletResponse response =
                  callWithBearerToken(ctx, "/api/entities", token);

              // then
              assertThat(response.getStatus()).isEqualTo(200);
            });
  }

  @Test
  void shouldAllowAUserBearerTokenThatHoldsThePermission() throws Exception {
    // given: matrix row "role-less user logs in" has a positive counterpart — a user WITH the
    // Optimize permission must keep working on the bearer path, same as today.
    final String token = userToken("demo");
    ccsmRunner()
        .run(
            ctx -> {
              // when
              final MockHttpServletResponse response =
                  callWithBearerToken(ctx, "/api/entities", token);

              // then
              assertThat(response.getStatus()).isEqualTo(200);
            });
  }

  @Test
  void shouldRejectAnUnauthenticatedRequestOnThePublicApiCarveOut() throws Exception {
    // given: pins the Global Constraints' assumption that /api/public/** is authenticated on the
    // same chain, not permit-all — presenting no bearer token at all must still be rejected. This
    // does not exercise OptimizeBearerPermissionFilter itself (it never runs without an
    // authenticated JwtAuthenticationToken), it pins the surrounding chain behavior the filter
    // depends on: if a future change moved this path into OptimizeSecurityPathAdapter's
    // unprotectedApiPaths(), an unauthenticated caller would start reaching it, and this test would
    // catch that even though a caller presenting a stale/wrong bearer token would not trip it.
    ccsmRunner()
        .run(
            ctx -> {
              // given
              final var request = new MockHttpServletRequest("GET", "/api/public/collection");
              final var response = new MockHttpServletResponse();

              // when
              resolveSecurityFilter(ctx).doFilter(request, response, new MockFilterChain());

              // then
              assertThat(response.getStatus()).isEqualTo(401);
            });
  }

  @Test
  void shouldRejectAnUnauthenticatedRequestOnTheIngestionVariableCarveOut() throws Exception {
    // given: same pin as above, for the other carve-out path.
    ccsmRunner()
        .run(
            ctx -> {
              // given
              final var request = new MockHttpServletRequest("GET", "/api/ingestion/variable");
              final var response = new MockHttpServletResponse();

              // when
              resolveSecurityFilter(ctx).doFilter(request, response, new MockFilterChain());

              // then
              assertThat(response.getStatus()).isEqualTo(401);
            });
  }
}
