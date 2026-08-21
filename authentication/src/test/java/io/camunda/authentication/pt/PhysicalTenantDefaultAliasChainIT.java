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
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityConfiguration;
import io.camunda.security.spring.handler.AuthFailureHandlerConfiguration;
import io.camunda.security.spring.oidc.JWSKeySelectorFactory;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilder;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilderConfiguration;
import io.camunda.security.spring.security.BaseSecurityConfiguration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
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
 * Integration test for the implicit {@code default} physical-tenant alias on a cluster with
 * <b>no</b> {@code camunda.physical-tenants.*} entries configured.
 *
 * <p>Distinct from {@link PhysicalTenantApiChainIsolationIT}, which covers isolation
 * <em>between</em> explicitly configured tenants and therefore runs with an empty root OIDC config.
 * Here the root config is the only config there is, and the subject under test is whether {@code
 * /physical-tenants/default} is reachable at all.
 */
class PhysicalTenantDefaultAliasChainIT {

  /** basePath + the host's apiPaths() = /physical-tenants/default/v2/** */
  private static final String DEFAULT_ALIAS_PATH = "/physical-tenants/default/v2/resource";

  private static JwksTestServer rootServer;

  @BeforeAll
  static void startServers() throws Exception {
    rootServer = JwksTestServer.start("key-root");
  }

  @AfterAll
  static void stopServers() {
    if (rootServer != null) {
      rootServer.stop();
    }
  }

  @Test
  void defaultAliasShouldAcceptRootIssuerTokenWhenNoPhysicalTenantsConfigured() {
    buildRunner()
        .run(
            ctx -> {
              // given — a cluster with a root OIDC provider and no physical tenants
              final var proxy = new FilterChainProxy(buildChains(ctx, rootOnlyEnv()));
              final var request = new MockHttpServletRequest("GET", DEFAULT_ALIAS_PATH);
              request.addHeader(
                  "Authorization",
                  "Bearer " + signForIssuer(rootServer, rootServer.issuerUri(), List.of()));
              final var response = new MockHttpServletResponse();
              final var downstream = new MockFilterChain();

              // when
              proxy.doFilter(request, response, downstream);

              // then
              assertThat(response.getStatus())
                  .as(
                      "a root-issuer token on the default alias must authenticate, not fall to the"
                          + " catch-all")
                  .isEqualTo(200);
              // Load-bearing, not redundant: MockHttpServletResponse starts at 200, so the status
              // assertion alone would also pass if no chain touched the request at all.
              assertThat(downstream.getRequest())
                  .as("the request must reach the downstream chain")
                  .isNotNull();
            });
  }

  // =========================================================================
  // Chain assembly helpers
  // =========================================================================

  /**
   * The method is set here <em>and</em> on the {@link MockEnvironment} in {@link #rootOnlyEnv()} on
   * purpose: this one configures CSL's properties bean in the context, that one is what the scope
   * provider reads. They are two separate consumers, not a duplicated setting.
   */
  private WebApplicationContextRunner buildRunner() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(ObjectMapperConfig.class, OcPathsConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                CamundaSecurityConfiguration.class,
                BaseSecurityConfiguration.class,
                ScopedApiSecurityChainBuilderConfiguration.class,
                AuthFailureHandlerConfiguration.class))
        .withPropertyValues("camunda.security.authentication.method=oidc");
  }

  /**
   * Derives descriptors from OC's {@link PhysicalTenantScopeProvider} and builds one scoped API
   * chain per descriptor, then appends CSL's catch-all last (lowest precedence).
   */
  private List<SecurityFilterChain> buildChains(
      final ApplicationContext ctx, final MockEnvironment env) {
    final var descriptors = new PhysicalTenantScopeProvider(env).get();

    final var jwsKeySelectorFactory = new JWSKeySelectorFactory();
    // Placeholder: the operative per-scope validator is built inside buildIssuerAwareDecoder from
    // each descriptor's own provider map.
    final var globalValidatorFactory =
        new TokenValidatorFactory(Map.of(), OidcConfiguration.DEFAULT_CLOCK_SKEW, List.of());
    final var scopedJwtDecoderFactory =
        new ScopedJwtDecoderFactory(
            new ScopedClientRegistrationFactory(),
            new OidcAccessTokenDecoderFactory(jwsKeySelectorFactory, globalValidatorFactory));
    final var chainBuilder = ctx.getBean(ScopedApiSecurityChainBuilder.class);

    final var chains = new ArrayList<SecurityFilterChain>();
    for (final ScopedSecurityDescriptor descriptor : descriptors) {
      try {
        chains.add(
            chainBuilder.buildScopedApiChain(
                ctx.getBean(HttpSecurity.class),
                descriptor.basePath(),
                descriptor.authentication(),
                () ->
                    scopedJwtDecoderFactory.buildIssuerAwareDecoder(descriptor.authentication())));
      } catch (final Exception ex) {
        throw new IllegalStateException("Failed to build chain for " + descriptor.basePath(), ex);
      }
    }
    // A request matching no scoped chain lands here: `/**` denyAll, answered as 404.
    chains.add(
        ctx.getBean("protectedUnhandledPathsSecurityFilterChain", SecurityFilterChain.class));
    return chains;
  }

  // =========================================================================
  // Environment builders
  // =========================================================================

  /** Root/cluster OIDC provider only — deliberately no {@code camunda.physical-tenants.*} keys. */
  private MockEnvironment rootOnlyEnv() {
    final var env = new MockEnvironment();
    env.setProperty("camunda.security.authentication.method", "oidc");
    env.setProperty("camunda.security.authentication.oidc.client-id", "root-client");
    env.setProperty("camunda.security.authentication.oidc.issuer-uri", rootServer.issuerUri());
    env.setProperty(
        "camunda.security.authentication.oidc.jwk-set-uri", rootServer.issuerUri() + "/jwks");
    env.setProperty("camunda.security.authentication.oidc.redirect-uri", "{baseUrl}/sso-callback");
    return env;
  }

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
  // Minimal host beans CSL's chain builder needs
  // =========================================================================

  @Configuration
  static class ObjectMapperConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  /** The OC host's {@link SecurityPathPort}, so CSL knows {@code /v2/**} is an API path. */
  @Configuration
  static class OcPathsConfig {

    @Bean
    SecurityPathPort securityPathPort() {
      return new SecurityPathAdapter();
    }
  }

  // =========================================================================
  // In-JVM JWKS + OIDC discovery server
  // =========================================================================

  /**
   * Minimal JWKS + discovery server; mirrors the pattern in {@link
   * PhysicalTenantApiChainIsolationIT}.
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
}
