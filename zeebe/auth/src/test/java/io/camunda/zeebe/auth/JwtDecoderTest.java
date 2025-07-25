/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.Date;
import org.junit.jupiter.api.Test;

public class JwtDecoderTest {

  @Test
  void shouldDecodeAndGetClaims() {
    // given
    final var jwtDecoder = new JwtDecoder(generateToken());

    // when
    final var claims = jwtDecoder.decode().getClaims();

    // then
    assertThat(claims)
        .containsEntry("role", "admin")
        .containsEntry("foo", "bar")
        .containsEntry("baz", "qux");
  }

  @Test
  void shouldDecodeAndGetClaimsWithoutDecodingFirst() {
    // given
    final var jwtDecoder = new JwtDecoder(generateToken());

    // when
    final var claims = jwtDecoder.getClaims();

    // then
    assertThat(claims)
        .containsEntry("role", "admin")
        .containsEntry("foo", "bar")
        .containsEntry("baz", "qux");
  }

  @Test
  void shouldRaiseExceptionWhenTokenIsNull() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new JwtDecoder(null));
  }

  @Test
  void shouldRaiseExceptionWhenTokenIsEmpty() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new JwtDecoder(""));
  }

  @Test
  void shouldRaiseExceptionWhenTokenIsInvalid() {
    // given
    final var decoder = new JwtDecoder("invalid-token");

    // then
    assertThatThrownBy(decoder::decode).isInstanceOf(RuntimeException.class);
  }

  private String generateToken() {
    final Algorithm algorithm = Algorithm.HMAC256("secret-key");

    return JWT.create()
        .withIssuer("test-issuer")
        .withSubject("test-user")
        .withAudience("test-audience")
        .withClaim("role", "admin")
        .withClaim("foo", "bar")
        .withClaim("baz", "qux")
        .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 60 * 1000)) // Expires in 1 hour
        .sign(algorithm);
  }
}
