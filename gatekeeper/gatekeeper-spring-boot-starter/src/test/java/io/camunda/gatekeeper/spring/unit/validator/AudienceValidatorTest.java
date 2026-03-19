/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gatekeeper.spring.validator.AudienceValidator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

final class AudienceValidatorTest {

  @Test
  void shouldSucceedWhenTokenAudienceMatchesValidAudience() {
    // given
    final var validator = new AudienceValidator(Set.of("my-client"));
    final var jwt = createJwtWithAudiences(List.of("my-client"));

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldSucceedWhenTokenHasMultipleAudiencesAndOneMatches() {
    // given
    final var validator = new AudienceValidator(Set.of("my-client"));
    final var jwt = createJwtWithAudiences(List.of("other-client", "my-client"));

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldFailWhenTokenAudienceDoesNotMatch() {
    // given
    final var validator = new AudienceValidator(Set.of("my-client"));
    final var jwt = createJwtWithAudiences(List.of("wrong-client"));

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
  }

  @Test
  void shouldFailWhenTokenHasNoAudience() {
    // given
    final var validator = new AudienceValidator(Set.of("my-client"));
    final var jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of("sub", "user"));

    // when
    final var result = validator.validate(jwt);

    // then
    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void shouldThrowWhenValidAudiencesIsEmpty() {
    assertThatThrownBy(() -> new AudienceValidator(Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("At least one valid audience must be provided");
  }

  private Jwt createJwtWithAudiences(final List<String> audiences) {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("alg", "RS256"),
        Map.of("sub", "user", "aud", audiences));
  }
}
