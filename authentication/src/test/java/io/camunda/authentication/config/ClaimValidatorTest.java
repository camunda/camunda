/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.TokenClaim;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

public class ClaimValidatorTest {
  @Test
  void shouldFailValidationForTokenWithNoMatchingClaim() {
    // given
    final var expectedClaims = Set.of(new TokenClaim("foo", "bar"));
    final var validator = new ClaimValidator(expectedClaims);
    final var token = createJwt(Map.of("something", "else", "not", "here"));

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void shouldPassValidationTokenWithExpectedSingleValueClaim() {
    // given
    final var expectedClaims =
        Set.of(new TokenClaim("foo", "bar"), new TokenClaim("not", "present"));
    final var validator = new ClaimValidator(expectedClaims);
    final var token = createJwt(Map.of("foo", "bar", "baz", "foz"));

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldPassValidationTokenWithExpectedArrayClaim() {
    // given
    final var expectedClaims =
        Set.of(new TokenClaim("foo", "two"), new TokenClaim("not", "present"));
    final var validator = new ClaimValidator(expectedClaims);
    final var token =
        createJwt(Map.of("foo", Arrays.asList(new String[] {"one", "two", "three"}), "baz", "foz"));

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  private static Jwt createJwt(final Map<String, Object> claims) {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        claims);
  }
}
