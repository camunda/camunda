/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies per-physical-tenant OIDC UserInfo claim augmentation over gRPC:
 *
 * <ul>
 *   <li>a PT with augmentation enabled resolves a claim that exists <em>only</em> in that PT's own
 *       {@code /userinfo} response (never on the JWT itself) — proving the real HTTP augmentation
 *       path runs end-to-end through the gRPC {@code AuthenticationInterceptor};
 *   <li>a PT's claims provider only ever calls its <em>own</em> IdP's {@code /userinfo} endpoint,
 *       never another PT's — each fake IdP below tracks its own hit count;
 *   <li>a PT with augmentation disabled never calls {@code /userinfo} at all (true passthrough, not
 *       a failed call) and therefore rejects a token whose relevant claim only exists there.
 * </ul>
 *
 * <p>Uses two small in-JVM fake IdPs (JDK {@link HttpServer} + a self-generated RSA key, mirroring
 * {@code JwksTestServer} in {@code PhysicalTenantApiChainIsolationIT}) instead of Keycloak, because
 * proving isolation requires counting requests per IdP, which a real IdP container does not expose.
 * Tokens are signed directly (no token endpoint needed) and handed to the gRPC client via a static
 * {@link CredentialsProvider} that just attaches the bearer header.
 */
@ZeebeIntegration
final class PhysicalTenantGrpcOidcUserInfoAugmentationIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String TENANT_C = "tenantc";
  private static final String SUBJECT = "alice";
  private static final String BEARER_PREFIX = "Bearer ";
  // Present only in each fake IdP's /userinfo response — never on the JWT — so a successful
  // authentication is direct proof that the claim was resolved via a real UserInfo call.
  private static final String USERINFO_ONLY_CLAIM = "client_ref";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .withTenant(TENANT_C, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  // PT-A / PT-B: distinct issuers, distinct fake IdPs, augmentation enabled on both.
  private static FakeIdpServer idpA;
  private static FakeIdpServer idpB;

  @BeforeAll
  static void startBroker() throws Exception {
    idpA = FakeIdpServer.start("tenant-a-ref");
    idpB = FakeIdpServer.start("tenant-b-ref");

    // withSecurityConfig sets the broker-wide OIDC issuer used by the default physical tenant;
    // non-default PTs override it below. redirectUri must be set because Spring's OAuth2 client
    // registration requires it as a mandatory field even in resource-server (gRPC) mode where no
    // browser redirect ever occurs.
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setIssuerUri(idpA.issuerUri());
          c.getAuthentication().getOidc().setClientId("default-client");
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });

    // PT-A and PT-B: augmentation enabled, each against its own fake IdP.
    configureTenant(TENANT_A, idpA, true);
    configureTenant(TENANT_B, idpB, true);
    // PT-C: same issuer as PT-A (so its tokens decode fine) but augmentation disabled — the
    // userinfo-only claim can therefore never resolve for it (passthrough).
    configureTenant(TENANT_C, idpA, false);

    BROKER.start();

    Awaitility.await("broker ready")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final var client = client(TENANT_A, idpA.signToken())) {
                client.newTopologyRequest().send().join();
              }
            });
  }

  @AfterAll
  static void stopServers() {
    if (idpA != null) {
      idpA.stop();
    }
    if (idpB != null) {
      idpB.stop();
    }
  }

  @BeforeEach
  void resetHitCounts() {
    // Readiness polling in @BeforeAll (and any earlier test) may have already called /userinfo;
    // each test signs its own fresh token (unique jti, so never cache-served), so resetting here
    // makes hit-count assertions independent of test order and prior runs.
    idpA.resetUserInfoHitCount();
    idpB.resetUserInfoHitCount();
  }

  @Test
  void shouldAugmentClaimsFromOwnUserInfoEndpointWhenEnabled() throws Exception {
    // given — a PT-A token that does NOT carry the client_ref claim on the JWT itself
    // when / then — accepted only because PT-A's own claims provider fetched it from PT-A's
    // /userinfo
    try (final var client = client(TENANT_A, idpA.signToken())) {
      assertThatNoException()
          .as("PT-A token is accepted — the claim is resolved via PT-A's own /userinfo")
          .isThrownBy(() -> client.newTopologyRequest().send().join());
    }

    assertThat(idpA.userInfoHitCount())
        .as("PT-A's claims provider must call PT-A's own /userinfo endpoint")
        .isEqualTo(1);
    assertThat(idpB.userInfoHitCount())
        .as("authenticating on PT-A must never reach PT-B's /userinfo endpoint")
        .isZero();
  }

  @Test
  void shouldNeverConsultAnotherTenantsUserInfoEndpoint() throws Exception {
    // when — PT-A and PT-B are each authenticated with their own token
    try (final var clientA = client(TENANT_A, idpA.signToken())) {
      clientA.newTopologyRequest().send().join();
    }
    assertThat(idpA.userInfoHitCount()).as("PT-A call hit PT-A's /userinfo").isEqualTo(1);
    assertThat(idpB.userInfoHitCount()).as("PT-A call must not hit PT-B's /userinfo").isZero();

    try (final var clientB = client(TENANT_B, idpB.signToken())) {
      clientB.newTopologyRequest().send().join();
    }

    // then — PT-B's call only ever reached its own IdP; PT-A's earlier count is untouched
    assertThat(idpB.userInfoHitCount()).as("PT-B call hit PT-B's /userinfo").isEqualTo(1);
    assertThat(idpA.userInfoHitCount())
        .as("PT-B's call must not retroactively hit PT-A's /userinfo")
        .isEqualTo(1);
  }

  @Test
  void shouldUsePassthroughAndRejectWhenUserInfoAugmentationDisabled() throws Exception {
    // given — a PT-C token; PT-C shares PT-A's issuer but has augmentation disabled, so the
    // claims provider is a no-op passthrough that never calls /userinfo
    // when / then — rejected: the client_ref claim only exists in /userinfo, which is never
    // consulted, so neither the username nor client-id claim resolves
    try (final var client = client(TENANT_C, idpA.signToken())) {
      assertThatThrownBy(() -> client.newTopologyRequest().send().join())
          .as("PT-C token is rejected — augmentation disabled means passthrough, JWT-only claims")
          .hasRootCauseInstanceOf(StatusRuntimeException.class)
          .rootCause()
          .satisfies(
              e ->
                  assertThat(((StatusRuntimeException) e).getStatus().getCode())
                      .isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    assertThat(idpA.userInfoHitCount())
        .as(
            "a userinfo-disabled PT must never call /userinfo — true passthrough, not a failed call")
        .isZero();
  }

  private static void configureTenant(
      final String tenantId, final FakeIdpServer idp, final boolean augmentationEnabled) {
    BROKER.withPtConfig(
        tenantId,
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(idp.issuerUri());
          oidc.setClientId("client-" + tenantId);
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
          // Both claims resolve from the same userinfo-only claim, so a successful authentication
          // (via either branch) is unambiguous proof the claim was merged in.
          oidc.setUsernameClaim(USERINFO_ONLY_CLAIM);
          oidc.setClientIdClaim(USERINFO_ONLY_CLAIM);
          oidc.getUserInfoAugmentation().setEnabled(augmentationEnabled);
        });
    // providers.assigned = ["oidc"] selects the per-PT default slot (authentication.oidc.*) as the
    // active provider for this PT; required for all non-default PTs under OIDC authentication.
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "oidc");
  }

  private static CamundaClient client(final String tenantId, final String token) {
    return TENANTS
        .newClientBuilder(BROKER, tenantId)
        .preferRestOverGrpc(false)
        .credentialsProvider(staticBearerToken(token))
        .build();
  }

  private static CredentialsProvider staticBearerToken(final String token) {
    return new CredentialsProvider() {
      @Override
      public void applyCredentials(final CredentialsApplier applier) {
        applier.put("Authorization", BEARER_PREFIX + token);
      }

      @Override
      public boolean shouldRetryRequest(final StatusCode statusCode) {
        return false;
      }
    };
  }

  /**
   * Minimal in-JVM fake IdP: serves OIDC discovery, a JWKS, and a {@code /userinfo} endpoint whose
   * hit count is tracked so tests can assert exactly which IdP a claims-augmentation call reached.
   * Tokens are signed locally with the same key the JWKS advertises — no token endpoint is needed
   * since the gRPC client supplies the bearer token directly via a static {@link
   * CredentialsProvider}.
   */
  private static final class FakeIdpServer {

    private final HttpServer server;
    private final String kid;
    private final JWSSigner signer;
    private final AtomicInteger userInfoHits;

    private FakeIdpServer(
        final HttpServer server,
        final String kid,
        final JWSSigner signer,
        final AtomicInteger userInfoHits) {
      this.server = server;
      this.kid = kid;
      this.signer = signer;
      this.userInfoHits = userInfoHits;
    }

    static FakeIdpServer start(final String userInfoClaimValue) throws Exception {
      final var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      final var pair = generator.generateKeyPair();
      final var kid = UUID.randomUUID().toString();
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
      final var userInfoHits = new AtomicInteger();

      final var discoveryDoc =
          """
          {
            "issuer": "%s",
            "authorization_endpoint": "%s/auth",
            "token_endpoint": "%s/token",
            "jwks_uri": "%s/jwks",
            "userinfo_endpoint": "%s/userinfo",
            "response_types_supported": ["code"],
            "subject_types_supported": ["public"],
            "id_token_signing_alg_values_supported": ["RS256"]
          }
          """
              .formatted(base, base, base, base, base);
      final var userInfoBody =
          "{\"sub\":\"%s\",\"%s\":\"%s\"}"
              .formatted(SUBJECT, USERINFO_ONLY_CLAIM, userInfoClaimValue);

      httpServer.createContext("/jwks", exchange -> respondJson(exchange, jwkSetJson));
      httpServer.createContext(
          "/.well-known/openid-configuration", exchange -> respondJson(exchange, discoveryDoc));
      httpServer.createContext(
          "/userinfo",
          exchange -> {
            // A real IdP rejects a UserInfo call with no (or malformed) bearer token; requiring
            // one here means a regression that drops the Authorization header on the outgoing
            // /userinfo call fails the test instead of silently returning canned claims.
            final var authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null
                || !authHeader.startsWith(BEARER_PREFIX)
                || authHeader.length() == BEARER_PREFIX.length()) {
              respond(exchange, 401, new byte[0]);
              return;
            }
            userInfoHits.incrementAndGet();
            respondJson(exchange, userInfoBody);
          });
      httpServer.start();
      return new FakeIdpServer(httpServer, kid, new RSASSASigner(jwk), userInfoHits);
    }

    private static void respondJson(final HttpExchange exchange, final String body)
        throws IOException {
      respond(exchange, 200, body.getBytes(UTF_8));
    }

    private static void respond(final HttpExchange exchange, final int status, final byte[] bytes)
        throws IOException {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      try (exchange) {
        exchange.getResponseBody().write(bytes);
      }
    }

    String issuerUri() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int userInfoHitCount() {
      return userInfoHits.get();
    }

    void resetUserInfoHitCount() {
      userInfoHits.set(0);
    }

    /** Signs a fresh token (random {@code jti}) so it is never served from the claims cache. */
    String signToken() throws Exception {
      final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
      final var claims =
          new JWTClaimsSet.Builder()
              .subject(SUBJECT)
              .issuer(issuerUri())
              .claim("scope", "openid")
              .jwtID(UUID.randomUUID().toString())
              .issueTime(Date.from(Instant.now()))
              .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
              .build();
      final var jwt = new SignedJWT(header, claims);
      jwt.sign(signer);
      return jwt.serialize();
    }

    void stop() {
      server.stop(0);
    }
  }
}
