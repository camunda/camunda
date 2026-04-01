/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.JWTProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Tests that {@link OidcAccessTokenDecoderFactory} disables Nimbus-level JWT claims verification
 * (including expiry checks) in favor of Spring Security's configurable {@link
 * JwtTimestampValidator}.
 *
 * <p>This is important because Nimbus's {@code DefaultJWTClaimsVerifier} uses a hardcoded 60-second
 * clock skew. By disabling it (matching Spring Security's own pattern in {@link
 * NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder}), the configurable Spring validator becomes the sole
 * authority for timestamp validation.
 */
class OidcAccessTokenDecoderFactoryTest {

  private static RSAKey rsaJwk;
  private static RSASSASigner signer;

  @BeforeAll
  static void generateKeys() throws JOSEException {
    rsaJwk =
        new RSAKeyGenerator(2048)
            .keyID("test-kid")
            .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
    signer = new RSASSASigner(rsaJwk);
  }

  @Test
  void shouldAcceptSlightlyExpiredTokenWhenWithinSpringClockSkew() throws Exception {
    // given a JWT that expired 30s ago (within the 60s Spring clock skew)
    final var expiredAt = Instant.now().minus(Duration.ofSeconds(30));
    final var token = createSignedJwt(expiredAt);
    final var decoder = createDecoder(Duration.ofSeconds(60));

    // when decoding the token
    final Jwt jwt = decoder.decode(token);

    // then it should succeed because Nimbus claims verification is disabled
    // and Spring's JwtTimestampValidator accepts it (30s < 60s clock skew)
    assertThat(jwt).isNotNull();
    assertThat(jwt.getExpiresAt()).isNotNull();
  }

  @Test
  void shouldRejectTokenExpiredBeyondSpringClockSkew() {
    // given a JWT that expired 90s ago (beyond the 60s Spring clock skew)
    final var expiredAt = Instant.now().minus(Duration.ofSeconds(90));
    final var token = createSignedJwt(expiredAt);
    final var decoder = createDecoder(Duration.ofSeconds(60));

    // when / then decoding should fail with Spring's JwtValidationException,
    // NOT Nimbus's BadJwtException("Expired JWT")
    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("Jwt expired");
  }

  @Test
  void shouldRejectTokenExpiredBeyondCustomClockSkew() {
    // given a JWT that expired 50s ago with a custom 45s clock skew
    final var expiredAt = Instant.now().minus(Duration.ofSeconds(50));
    final var token = createSignedJwt(expiredAt);
    final var decoder = createDecoder(Duration.ofSeconds(45));

    // when / then decoding should fail via Spring's validator (50s > 45s skew)
    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("Jwt expired");
  }

  @Test
  void shouldAcceptTokenExpiredWithinCustomClockSkew() throws Exception {
    // given a JWT that expired 40s ago with a custom 45s clock skew
    final var expiredAt = Instant.now().minus(Duration.ofSeconds(40));
    final var token = createSignedJwt(expiredAt);
    final var decoder = createDecoder(Duration.ofSeconds(45));

    // when decoding the token
    final Jwt jwt = decoder.decode(token);

    // then it succeeds (40s < 45s clock skew)
    assertThat(jwt).isNotNull();
  }

  @Test
  void shouldAcceptValidNonExpiredToken() throws Exception {
    // given a JWT that expires in 5 minutes
    final var expiresAt = Instant.now().plus(Duration.ofMinutes(5));
    final var token = createSignedJwt(expiresAt);
    final var decoder = createDecoder(Duration.ofSeconds(60));

    // when decoding the token
    final Jwt jwt = decoder.decode(token);

    // then it succeeds
    assertThat(jwt).isNotNull();
    assertThat(jwt.getSubject()).isEqualTo("test-subject");
  }

  @Test
  void shouldRejectTokenWithInvalidSignature() {
    // given a JWT signed with a different key
    final var token = createSignedJwtWithDifferentKey(Instant.now().plus(Duration.ofMinutes(5)));
    final var decoder = createDecoder(Duration.ofSeconds(60));

    // when / then the signature verification should fail
    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(BadJwtException.class)
        .hasMessageContaining("Signed JWT rejected");
  }

  private NimbusJwtDecoder createDecoder(final Duration clockSkew) {
    final var factory = new OidcAccessTokenDecoderFactory(null, null);
    final var jwkSource =
        new com.nimbusds.jose.jwk.source.ImmutableJWKSet<SecurityContext>(
            new com.nimbusds.jose.jwk.JWKSet(rsaJwk.toPublicJWK()));
    final var keySelector = new JWSVerificationKeySelector<>(Set.of(JWSAlgorithm.RS256), jwkSource);

    final JWTProcessor<SecurityContext> processor =
        factory.createAndCustomizeJwtProcessor(p -> p.setJWSKeySelector(keySelector));

    final var decoder = new NimbusJwtDecoder(processor);
    final var timestampValidator = new JwtTimestampValidator(clockSkew);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator));
    return decoder;
  }

  private static String createSignedJwt(final Instant expirationTime) {
    try {
      // Set issueTime to 5 minutes before expiry so that iat < exp always holds
      final var issuedAt = expirationTime.minus(Duration.ofMinutes(5));
      final var claims =
          new JWTClaimsSet.Builder()
              .issuer("https://test-issuer.example.com")
              .subject("test-subject")
              .audience("test-audience")
              .issueTime(Date.from(issuedAt))
              .expirationTime(Date.from(expirationTime))
              .build();

      final var signedJWT =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(), claims);
      signedJWT.sign(signer);
      return signedJWT.serialize();
    } catch (final JOSEException e) {
      throw new RuntimeException(e);
    }
  }

  private static String createSignedJwtWithDifferentKey(final Instant expirationTime) {
    try {
      final var differentKey =
          new RSAKeyGenerator(2048).keyID("different-kid").algorithm(JWSAlgorithm.RS256).generate();
      final var differentSigner = new RSASSASigner(differentKey);

      final var claims =
          new JWTClaimsSet.Builder()
              .issuer("https://test-issuer.example.com")
              .subject("test-subject")
              .expirationTime(Date.from(expirationTime))
              .build();

      final var signedJWT =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("different-kid").build(), claims);
      signedJWT.sign(differentSigner);
      return signedJWT.serialize();
    } catch (final JOSEException e) {
      throw new RuntimeException(e);
    }
  }
}
