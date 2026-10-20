/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Runs the CCSM component access policy against the real Identity SDK token verification, so the
 * decision rests on the exceptions the SDK actually throws rather than on stubbed ones. The tokens
 * are signed by an in-JVM JWKS server.
 */
class OptimizeCcsmComponentAccessPolicyIdentitySdkTest {

  private static final String OPTIMIZE_AUDIENCE = "optimize-api";
  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(builder -> builder.user("kermit"));

  private static JwksTestServer server;

  @BeforeAll
  static void startServer() throws Exception {
    server = JwksTestServer.start("component-access-sdk-key");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void shouldAllowATokenThatHoldsTheOptimizePermission() throws Exception {
    // given
    final String token = token(OPTIMIZE_AUDIENCE, List.of("write:*"), validUntil());

    // when
    final Either<String, Void> access = policyFor(token).checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isRight()).isTrue();
  }

  @Test
  void shouldDenyATokenWithoutTheOptimizePermission() throws Exception {
    // given
    final String token = token(OPTIMIZE_AUDIENCE, List.of(), validUntil());

    // when
    final Either<String, Void> access = policyFor(token).checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isLeft()).isTrue();
  }

  @Test
  void shouldDenyATokenForAnotherAudience() throws Exception {
    // given
    final String token = token("another-api", List.of(), validUntil());

    // when
    final Either<String, Void> access = policyFor(token).checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isLeft()).isTrue();
  }

  @Test
  void shouldAllowAnExpiredTokenForTheOptimizeAudience() throws Exception {
    // given
    final String token = token(OPTIMIZE_AUDIENCE, List.of("write:*"), expired());

    // when
    final Either<String, Void> access = policyFor(token).checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isRight()).isTrue();
  }

  @Test
  void shouldDenyAnExpiredTokenForAnotherAudience() throws Exception {
    // The expiry leniency must not cover a token that fails another check as well.
    // given
    final String token = token("another-api", List.of(), expired());

    // when
    final Either<String, Void> access = policyFor(token).checkAccess(AUTHENTICATION);

    // then
    assertThat(access.isLeft()).isTrue();
  }

  private static OptimizeCcsmComponentAccessPolicy policyFor(final String token) {
    final Identity identity =
        new Identity(
            new IdentityConfiguration.Builder()
                .withType(IdentityConfiguration.Type.KEYCLOAK.name())
                .withIssuer(server.issuerUri())
                .withIssuerBackendUrl(server.issuerUri())
                .withBaseUrl(server.issuerUri())
                .withClientId("optimize")
                .withClientSecret("secret")
                .withAudience(OPTIMIZE_AUDIENCE)
                .build());
    @SuppressWarnings("unchecked")
    final CCSMTokenService tokenService =
        spy(
            new CCSMTokenService(
                mock(AuthCookieService.class),
                ConfigurationServiceBuilder.createDefaultConfiguration(),
                identity,
                mock(ObjectProvider.class),
                mock(ObjectProvider.class)));
    // Only the lookup of the session token is replaced; the verification is the real one.
    doReturn(Optional.of(token)).when(tokenService).getCurrentUserAuthToken();
    return new OptimizeCcsmComponentAccessPolicy(tokenService);
  }

  private static String token(
      final String audience, final List<String> permissions, final Instant expiresAt)
      throws Exception {
    final var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(server.kid()).build();
    final var jwt =
        new SignedJWT(
            header,
            new JWTClaimsSet.Builder()
                .issuer(server.issuerUri())
                .subject("kermit")
                .audience(audience)
                .claim("permissions", Map.of(audience, permissions))
                .issueTime(Date.from(expiresAt.minus(Duration.ofMinutes(5))))
                .expirationTime(Date.from(expiresAt))
                .build());
    jwt.sign(server.signer());
    return jwt.serialize();
  }

  private static Instant validUntil() {
    return Instant.now().plus(Duration.ofMinutes(5));
  }

  private static Instant expired() {
    // Well beyond the clock skew the SDK accepts.
    return Instant.now().minus(Duration.ofHours(1));
  }
}
