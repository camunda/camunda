/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AudienceValidatorTest {

  @Test
  void shouldFailWhenAudienceIsNull() {
    // given
    final var validator = new AudienceValidator(Set.of("my-app"));
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "user1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).anyMatch(error -> "invalid_token".equals(error.getErrorCode()));
  }

  @Test
  void shouldFailWhenAudienceIsEmpty() {
    // given
    final var validator = new AudienceValidator(Set.of("my-app"));
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("aud", List.of())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).anyMatch(error -> "invalid_token".equals(error.getErrorCode()));
  }

  @Test
  void shouldFailWhenAudienceDoesNotMatch() {
    // given
    final var validator = new AudienceValidator(Set.of("my-app"));
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("aud", List.of("other"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).anyMatch(error -> "invalid_token".equals(error.getErrorCode()));
  }

  @Test
  void shouldSucceedWhenAudienceMatches() {
    // given
    final var validator = new AudienceValidator(Set.of("my-app"));
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("aud", List.of("my-app"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldSucceedWhenAnyAudienceMatches() {
    // given
    final var validator = new AudienceValidator(Set.of("my-app"));
    final Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("aud", List.of("other", "my-app"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
  }
}
