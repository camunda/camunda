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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class CachingJwtDecoderTest {

  private JwtDecoder delegate;
  private CachingJwtDecoder cachingDecoder;

  @BeforeEach
  void setUp() {
    delegate = mock(JwtDecoder.class);
    cachingDecoder = new CachingJwtDecoder(delegate, 100, Duration.ofHours(1));
  }

  @Test
  void delegatesOnFirstCall() {
    final var jwt = buildJwt(Instant.now().plusSeconds(300));
    when(delegate.decode("token")).thenReturn(jwt);

    final var result = cachingDecoder.decode("token");

    assertThat(result).isSameAs(jwt);
    verify(delegate, times(1)).decode("token");
  }

  @Test
  void returnsCachedJwtOnSubsequentCallsWithoutCallingDelegate() {
    final var jwt = buildJwt(Instant.now().plusSeconds(300));
    when(delegate.decode("token")).thenReturn(jwt);

    cachingDecoder.decode("token");
    cachingDecoder.decode("token");
    cachingDecoder.decode("token");

    verify(delegate, times(1)).decode("token");
  }

  @Test
  void differentTokensAreEachDecodedOnce() {
    final var jwt1 = buildJwt(Instant.now().plusSeconds(300));
    final var jwt2 = buildJwt(Instant.now().plusSeconds(300));
    when(delegate.decode("token1")).thenReturn(jwt1);
    when(delegate.decode("token2")).thenReturn(jwt2);

    cachingDecoder.decode("token1");
    cachingDecoder.decode("token1");
    cachingDecoder.decode("token2");
    cachingDecoder.decode("token2");

    verify(delegate, times(1)).decode("token1");
    verify(delegate, times(1)).decode("token2");
  }

  @Test
  void invalidTokenIsNotCached() {
    when(delegate.decode("bad-token")).thenThrow(new BadJwtException("invalid"));

    assertThatThrownBy(() -> cachingDecoder.decode("bad-token"))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> cachingDecoder.decode("bad-token"))
        .isInstanceOf(JwtException.class);

    // delegate must be called each time since the error is not cached
    verify(delegate, times(2)).decode("bad-token");
  }

  @Test
  void jwtExceptionIsPropagatedUnwrapped() {
    final var originalException = new BadJwtException("signature invalid");
    when(delegate.decode("token")).thenThrow(originalException);

    assertThatThrownBy(() -> cachingDecoder.decode("token"))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("signature invalid");
  }

  @Test
  void tokenWithNullExpiryUsesMaxLifetimeAsTtl() {
    // A token with no exp should still be cached (uses maxTokenLifetime cap)
    final var jwt = buildJwt(null);
    when(delegate.decode("token")).thenReturn(jwt);

    cachingDecoder.decode("token");
    cachingDecoder.decode("token");

    verify(delegate, times(1)).decode("token");
  }

  private static Jwt buildJwt(final Instant expiresAt) {
    return new Jwt(
        "raw-token-value",
        Instant.now(),
        expiresAt,
        Map.of("alg", "RS256"),
        Map.of("sub", "test-user"));
  }
}
