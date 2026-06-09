/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.camunda.authentication.config.spi.SecurityPathAdapter;
import io.camunda.security.api.model.config.ScopedSecurityDescriptor;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.handler.AuthFailureHandlerConfiguration;
import io.camunda.security.spring.oidc.JWSKeySelectorFactory;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilder;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilderConfiguration;
import io.camunda.security.spring.security.BaseSecurityConfiguration;
import io.camunda.security.spring.security.OidcResourceServerCustomizer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Integration test for per-physical-tenant API security-chain isolation.
 *
 * <p>Proves four scenarios using two physical tenants (PT-A and PT-B), each backed by a real in-JVM
 * JWKS server, with no Testcontainers, no Elasticsearch, and no Keycloak:
 *
 * <ol>
 *   <li><b>Own-chain accept</b>: a valid token from PT-A's issuer on PT-A's path → authenticated
 *       (200).
 *   <li><b>Cross-issuer reject</b>: a token from PT-B's (different) issuer on PT-A's path → 401.
 *   <li><b>Shared-IdP audience reject</b>: two PTs sharing the same issuer but declaring different
 *       audiences — a token with PT-A's audience on PT-B's path → 401.
 *   <li><b>Unauthenticated</b>: no token on either PT path → 401.
 * </ol>
 *
 * <p>The test uses the OC {@link PhysicalTenantScopeProvider} to produce {@link
 * ScopedSecurityDescriptor}s from a {@link MockEnvironment}, proving the full OC scope-provider →
 * CSL decoder → isolating chain pipeline end-to-end.
 */
class PhysicalTenantApiChainIsolationIT {

  // -------------------------------------------------------------------------
  // Per-PT path shapes (basePath + host apiPaths() = /physical-tenants/<id>/v2/**)
  // -------------------------------------------------------------------------

  private static final String PATH_PT_A = "/physical-tenants/pta/v2/resource";
  private static final String PATH_PT_B = "/physical-tenants/ptb/v2/resource";

  // -------------------------------------------------------------------------
  // In-JVM JWKS servers — started once for all scenarios
  // -------------------------------------------------------------------------

  private static JwksTestServer serverA; // PT-A's own issuer
  private static JwksTestServer serverB; // PT-B's different issuer
  private static JwksTestServer serverShared; // shared issuer used in scenario 3

  @BeforeAll
  static void startServers() throws Exception {
    serverA = JwksTestServer.start("key-a");
    serverB = JwksTestServer.start("key-b");
    serverShared = JwksTestServer.start("key-shared");
  }

  @AfterAll
  static void stopServers() {
    if (serverA != null) {
      serverA.stop();
    }
    if (serverB != null) {
      serverB.stop();
    }
    if (serverShared != null) {
      serverShared.stop();
    }
  }

  // -------------------------------------------------------------------------
  // Scenario 1: own-chain accept
  // -------------------------------------------------------------------------

  /**
   * A valid token from PT-A's issuer on PT-A's path must be authenticated (request passes through
   * to the downstream filter chain with HTTP 200).
   */
  @Test
  void ownChainAcceptPtATokenOnPtAPathShouldReturn200() {
    buildRunner(twoDistinctIssuersProperties())
        .run(
            ctx -> {
              final var chains = buildChainsFromProvider(ctx, twoDistinctIssuersEnv());
              final var proxy = new FilterChainProxy(chains);

              final var request = new MockHttpServletRequest("GET", PATH_PT_A);
              final var tokenForA = signForIssuer(serverA, serverA.issuerUri(), List.of());
              request.addHeader("Authorization", "Bearer " + tokenForA);
              final var response = new MockHttpServletResponse();
              final var downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              // Authenticated: request must have reached the downstream filter
              assertThat(downstream.getRequest())
                  .as("PT-A token on PT-A path should be authenticated and reach downstream")
                  .isNotNull();
              assertThat(response.getStatus())
                  .as("PT-A token on PT-A path should yield 200")
                  .isEqualTo(200);
            });
  }

  // -------------------------------------------------------------------------
  // Scenario 2: cross-issuer reject
  // -------------------------------------------------------------------------

  /**
   * A token signed by PT-B's (different) issuer presented on PT-A's path must be rejected with HTTP
   * 401 — PT-A's chain only accepts tokens from PT-A's issuer.
   */
  @Test
  void crossIssuerRejectPtBTokenOnPtAPathShouldReturn401() {
    buildRunner(twoDistinctIssuersProperties())
        .run(
            ctx -> {
              final var chains = buildChainsFromProvider(ctx, twoDistinctIssuersEnv());
              final var proxy = new FilterChainProxy(chains);

              final var request = new MockHttpServletRequest("GET", PATH_PT_A);
              // Token from PT-B's issuer (different key and issuer) presented on PT-A's path
              final var tokenFromB = signForIssuer(serverB, serverB.issuerUri(), List.of());
              request.addHeader("Authorization", "Bearer " + tokenFromB);
              final var response = new MockHttpServletResponse();

              proxy.doFilter(request, response, new MockFilterChain());

              assertThat(response.getStatus())
                  .as("PT-B token on PT-A path (cross-issuer) must be rejected with 401")
                  .isEqualTo(401);
            });
  }

  // -------------------------------------------------------------------------
  // Scenario 3: shared-IdP audience reject
  // -------------------------------------------------------------------------

  /**
   * Two PTs share the SAME issuer (same JWKS server) but each declares a DIFFERENT audience. A
   * token carrying PT-A's audience ({@code aud-pta}) presented on PT-B's path must be rejected with
   * HTTP 401 — PT-B's chain expects {@code aud-ptb}.
   *
   * <p>This scenario specifically guards the CSL fix that ensures per-scope audience validation is
   * performed against the scope's own provider config rather than a global singleton.
   */
  @Test
  void sharedIdpAudienceRejectPtATokenOnPtBPathShouldReturn401() {
    buildRunner(sharedIssuerDifferentAudiencesProperties())
        .run(
            ctx -> {
              final var chains = buildChainsFromProvider(ctx, sharedIssuerDifferentAudiencesEnv());
              final var proxy = new FilterChainProxy(chains);

              final var request = new MockHttpServletRequest("GET", PATH_PT_B);
              // Token carrying PT-A's audience (aud-pta) — only PT-A's chain should accept it
              final var tokenWithAudA =
                  signForIssuer(serverShared, serverShared.issuerUri(), List.of("aud-pta"));
              request.addHeader("Authorization", "Bearer " + tokenWithAudA);
              final var response = new MockHttpServletResponse();

              proxy.doFilter(request, response, new MockFilterChain());

              assertThat(response.getStatus())
                  .as(
                      "Token with PT-A's audience on PT-B's path (shared-issuer/different-audience)"
                          + " must be rejected with 401")
                  .isEqualTo(401);
            });
  }

  // -------------------------------------------------------------------------
  // Scenario 4: unauthenticated
  // -------------------------------------------------------------------------

  /** A request with no Authorization header on PT-A's path must be rejected with HTTP 401. */
  @Test
  void unauthenticatedNoBearerTokenOnPtAPathShouldReturn401() {
    buildRunner(twoDistinctIssuersProperties())
        .run(
            ctx -> {
              final var chains = buildChainsFromProvider(ctx, twoDistinctIssuersEnv());
              final var proxy = new FilterChainProxy(chains);

              final var request = new MockHttpServletRequest("GET", PATH_PT_A);
              // No Authorization header
              final var response = new MockHttpServletResponse();

              proxy.doFilter(request, response, new MockFilterChain());

              assertThat(response.getStatus())
                  .as("Unauthenticated request on PT-A path must yield 401")
                  .isEqualTo(401);
            });
  }

  // -------------------------------------------------------------------------
  // Scenario 5: unknown (unconfigured) tenant
  // -------------------------------------------------------------------------

  /**
   * A request for an unconfigured physical tenant matches no per-scope chain and must fall to CSL's
   * catch-all (`/**` denyAll → 404), not slip through unsecured. This locks in the delegation that
   * replaced {@code PhysicalTenantInterceptor}'s explicit 404 (ADR-0003): if CSL's catch-all ever
   * changes its response code, or a per-scope chain starts over-matching unknown tenants, this
   * fails.
   */
  @Test
  void unknownPhysicalTenantShouldReturn404() {
    buildRunner(twoDistinctIssuersProperties())
        .run(
            ctx -> {
              final var chains = buildChainsFromProvider(ctx, twoDistinctIssuersEnv());
              final var proxy = new FilterChainProxy(chains);

              // "nonexistent" is not among the configured tenants (pta, ptb).
              final var request =
                  new MockHttpServletRequest("GET", "/physical-tenants/nonexistent/v2/resource");
              final var response = new MockHttpServletResponse();
              final var downstream = new MockFilterChain();

              proxy.doFilter(request, response, downstream);

              assertThat(downstream.getRequest())
                  .as("unknown tenant must not reach the downstream chain")
                  .isNull();
              assertThat(response.getStatus())
                  .as("unknown physical tenant must be rejected by CSL's catch-all with 404")
                  .isEqualTo(404);
            });
  }

  // =========================================================================
  // Chain assembly helpers
  // =========================================================================

  /**
   * Builds a {@link WebApplicationContextRunner} seeded with the minimum CSL infrastructure beans
   * needed to assemble scoped chains. The actual per-PT properties are not injected here — they are
   * applied in {@link #buildChainsFromProvider} via a {@link MockEnvironment} so issuer URIs that
   * depend on dynamically-allocated server ports can be resolved correctly.
   */
  private WebApplicationContextRunner buildRunner(final String[] properties) {
    return new WebApplicationContextRunner()
        .withUserConfiguration(ObjectMapperConfig.class, OcPathsConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                CamundaSecurityConfiguration.class,
                BaseSecurityConfiguration.class,
                ScopedApiSecurityChainBuilderConfiguration.class,
                AuthFailureHandlerConfiguration.class))
        .withPropertyValues(properties);
  }

  /**
   * Uses OC's {@link PhysicalTenantScopeProvider} to derive {@link ScopedSecurityDescriptor}s from
   * the given {@link MockEnvironment}, then builds one {@link SecurityFilterChain} per descriptor
   * using {@link ScopedJwtDecoderFactory} + {@link ScopedApiSecurityChainBuilder}.
   *
   * <p>This proves the full OC integration: the OC scope-provider component transforms the Spring
   * {@link org.springframework.core.env.Environment} into descriptors, and the CSL chain-builder
   * turns those descriptors into real, JWT-validating security filter chains.
   */
  private List<SecurityFilterChain> buildChainsFromProvider(
      final org.springframework.context.ApplicationContext ctx, final MockEnvironment env) {
    // The environment carries the full PT + root OIDC config including issuer URIs pointing at the
    // dynamically-allocated JWKS servers.
    env.setProperty("camunda.security.authentication.method", "oidc");

    // OC's scope provider: reads camunda.physical-tenants.* and produces ScopedSecurityDescriptors
    final var scopeProvider = new PhysicalTenantScopeProvider(env);
    final var descriptors = scopeProvider.get();
    assertThat(descriptors).as("scope provider must return at least one descriptor").isNotEmpty();

    // CSL decoder factory wired from scratch — no Spring context required; mirrors the CSL test
    // helper in ScopedJwtDecoderFactoryTest.
    final var jwsKeySelectorFactory = new JWSKeySelectorFactory();
    // TokenValidatorFactory is scoped per-descriptor inside
    // ScopedJwtDecoderFactory.buildIssuerAwareDecoder;
    // the global one here is just a placeholder — the real per-scope factory is built inside
    // ScopedJwtDecoderFactory.buildIssuerAwareDecoder using the descriptor's own provider map.
    final var globalValidatorFactory =
        new TokenValidatorFactory(
            java.util.Map.of(),
            io.camunda.security.api.model.config.oidc.OidcConfiguration.DEFAULT_CLOCK_SKEW,
            List.of());
    final var decoderFactory =
        new OidcAccessTokenDecoderFactory(jwsKeySelectorFactory, globalValidatorFactory);
    final var registrationFactory = new ScopedClientRegistrationFactory();
    final var scopedJwtDecoderFactory =
        new ScopedJwtDecoderFactory(registrationFactory, decoderFactory);

    // CSL chain builder from the Spring context (so it inherits the wired SecurityPathPort etc.)
    final var properties = ctx.getBean(CamundaSecurityLibraryProperties.class);
    final var authFailureHandler =
        ctx.getBean(io.camunda.security.spring.handler.AuthFailureHandler.class);
    final var pathPort = ctx.getBean(SecurityPathPort.class);
    @SuppressWarnings("unchecked")
    final ObjectProvider<OidcResourceServerCustomizer> customizers =
        (ObjectProvider<OidcResourceServerCustomizer>)
            ctx.getBeanProvider(OidcResourceServerCustomizer.class);

    final var chainBuilder =
        new ScopedApiSecurityChainBuilder(properties, authFailureHandler, pathPort, customizers);

    final var chains = new java.util.ArrayList<SecurityFilterChain>();
    for (final var descriptor : descriptors) {
      // Get a fresh HttpSecurity prototype from the Spring Security bean factory
      final var http = ctx.getBean(HttpSecurity.class);
      try {
        final var chain =
            chainBuilder.buildScopedApiChain(
                http,
                descriptor.basePath(),
                descriptor.authentication(),
                () -> scopedJwtDecoderFactory.buildIssuerAwareDecoder(descriptor.authentication()));
        chains.add(chain);
      } catch (final Exception ex) {
        throw new IllegalStateException("Failed to build chain for " + descriptor.basePath(), ex);
      }
    }
    // Append CSL's catch-all chain (BaseSecurityConfiguration, ORDER_UNHANDLED, lowest precedence):
    // a request matching no per-scope chain — e.g. an unconfigured tenant — falls to its `/**`
    // denyAll and is rejected with 404, rather than slipping through unsecured. The per-scope
    // chains
    // above are more specific and still win for configured tenants.
    chains.add(
        ctx.getBean("protectedUnhandledPathsSecurityFilterChain", SecurityFilterChain.class));
    return chains;
  }

  // =========================================================================
  // Environment / property builders
  // =========================================================================

  /**
   * Configures two physical tenants with distinct issuers and JWKS servers:
   *
   * <ul>
   *   <li>PT-A ({@code pta}): uses {@code serverA} (its own issuer)
   *   <li>PT-B ({@code ptb}): uses {@code serverB} (a different issuer)
   * </ul>
   */
  private MockEnvironment twoDistinctIssuersEnv() {
    final var env = new MockEnvironment();
    // Root method
    env.setProperty("camunda.security.authentication.method", "oidc");

    // Root named providers — one per PT
    env.setProperty("camunda.security.authentication.providers.oidc.pta.client-id", "client-pta");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.pta.issuer-uri", serverA.issuerUri());
    env.setProperty(
        "camunda.security.authentication.providers.oidc.pta.jwk-set-uri",
        serverA.issuerUri() + "/jwks");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.pta.redirect-uri",
        "{baseUrl}/sso-callback");

    env.setProperty("camunda.security.authentication.providers.oidc.ptb.client-id", "client-ptb");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.ptb.issuer-uri", serverB.issuerUri());
    env.setProperty(
        "camunda.security.authentication.providers.oidc.ptb.jwk-set-uri",
        serverB.issuerUri() + "/jwks");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.ptb.redirect-uri",
        "{baseUrl}/sso-callback");

    // PT-A assigns only its own provider
    env.setProperty(
        "camunda.physical-tenants.pta.security.authentication.providers.assigned[0]", "pta");
    // PT-B assigns only its own provider
    env.setProperty(
        "camunda.physical-tenants.ptb.security.authentication.providers.assigned[0]", "ptb");

    return env;
  }

  /**
   * Returns the Spring property values (as {@code key=value} strings) for the two-distinct-issuers
   * scenario so they can be passed to {@link WebApplicationContextRunner#withPropertyValues}.
   */
  private String[] twoDistinctIssuersProperties() {
    return new String[] {
      "camunda.security.authentication.method=oidc",
    };
  }

  /**
   * Configures two physical tenants sharing the SAME issuer (same JWKS server) but each with a
   * DIFFERENT audience:
   *
   * <ul>
   *   <li>PT-A ({@code pta}): issuer = {@code serverShared}, audience = {@code aud-pta}
   *   <li>PT-B ({@code ptb}): issuer = {@code serverShared}, audience = {@code aud-ptb}
   * </ul>
   */
  private MockEnvironment sharedIssuerDifferentAudiencesEnv() {
    final var env = new MockEnvironment();
    env.setProperty("camunda.security.authentication.method", "oidc");

    // Both PTs share the same issuer (serverShared) — audience is what isolates them
    env.setProperty("camunda.security.authentication.providers.oidc.pta.client-id", "client-pta");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.pta.issuer-uri", serverShared.issuerUri());
    env.setProperty(
        "camunda.security.authentication.providers.oidc.pta.jwk-set-uri",
        serverShared.issuerUri() + "/jwks");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.pta.redirect-uri",
        "{baseUrl}/sso-callback");
    env.setProperty("camunda.security.authentication.providers.oidc.pta.audiences[0]", "aud-pta");

    env.setProperty("camunda.security.authentication.providers.oidc.ptb.client-id", "client-ptb");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.ptb.issuer-uri", serverShared.issuerUri());
    env.setProperty(
        "camunda.security.authentication.providers.oidc.ptb.jwk-set-uri",
        serverShared.issuerUri() + "/jwks");
    env.setProperty(
        "camunda.security.authentication.providers.oidc.ptb.redirect-uri",
        "{baseUrl}/sso-callback");
    env.setProperty("camunda.security.authentication.providers.oidc.ptb.audiences[0]", "aud-ptb");

    // Each PT assigns only its own provider
    env.setProperty(
        "camunda.physical-tenants.pta.security.authentication.providers.assigned[0]", "pta");
    env.setProperty(
        "camunda.physical-tenants.ptb.security.authentication.providers.assigned[0]", "ptb");

    return env;
  }

  private String[] sharedIssuerDifferentAudiencesProperties() {
    return new String[] {
      "camunda.security.authentication.method=oidc",
    };
  }

  // =========================================================================
  // JWT signing helper
  // =========================================================================

  private static String signForIssuer(
      final JwksTestServer server, final String issuer, final List<String> audiences)
      throws Exception {
    final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(server.kid()).build();
    final var builder =
        new JWTClaimsSet.Builder()
            .subject("alice")
            .issuer(issuer)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(60)));
    if (!audiences.isEmpty()) {
      builder.audience(audiences);
    }
    final var jwt = new SignedJWT(header, builder.build());
    jwt.sign(server.signer());
    return jwt.serialize();
  }

  // =========================================================================
  // In-JVM JWKS + OIDC discovery server
  // =========================================================================

  /**
   * Minimal JWKS + OIDC discovery HTTP server backed by a freshly generated RSA key pair. Serves:
   *
   * <ul>
   *   <li>{@code /jwks} — the public JWK set (for token verification)
   *   <li>{@code /.well-known/openid-configuration} — a discovery document pointing back to this
   *       server
   * </ul>
   *
   * <p>Mirrors the {@code JwksTestServer} pattern from CSL's {@code ScopedJwtDecoderFactoryTest}.
   */
  private static final class JwksTestServer {

    private final HttpServer server;
    private final String kid;
    private final JWSSigner signer;

    private JwksTestServer(final HttpServer server, final String kid, final JWSSigner signer) {
      this.server = server;
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
            final var body = jwkSetJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (exchange) {
              exchange.getResponseBody().write(body);
            }
          });
      httpServer.createContext(
          "/.well-known/openid-configuration",
          exchange -> {
            final var body = discoveryDoc.getBytes(StandardCharsets.UTF_8);
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
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void stop() {
      server.stop(0);
    }
  }

  // =========================================================================
  // Spring configuration fragments for the WebApplicationContextRunner
  // =========================================================================

  /** Provides an {@link ObjectMapper} so CSL's {@link AuthFailureHandlerConfiguration} can wire. */
  @Configuration
  static class ObjectMapperConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  /**
   * Provides the OC host's {@link SecurityPathPort} so CSL's chain builder knows which paths to
   * protect ({@code /v2/**} among others).
   */
  @Configuration
  static class OcPathsConfig {

    @Bean
    SecurityPathPort securityPathPort() {
      return new SecurityPathAdapter();
    }
  }
}
