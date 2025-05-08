/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.authentication.config.ClusterValidator.CLUSTER_CLAIM_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

final class ClusterValidatorTest {

  @Test
  void shouldThrowExceptionWhenClusterIdIsNull() {
    // when/then
    assertThatThrownBy(() -> new ClusterValidator(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("clusterId must not be null");
  }

  @Test
  void shouldValidateTokenWithMatchingClusterId() {
    // given
    final var clusterId = "valid-cluster-id";
    final var validator = new ClusterValidator(clusterId);
    final var token = createJwtWithCluster(clusterId);

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldValidateTokenWithNoClusterClaim() {
    // given
    final var clusterId = "valid-cluster-id";
    final var validator = new ClusterValidator(clusterId);
    final var token = createJwtWithoutClusterClaim();

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldRejectTokenWithNonMatchingClusterId() {
    // given
    final var clusterId = "valid-cluster-id";
    final var validator = new ClusterValidator(clusterId);
    final var token = createJwtWithCluster("invalid-cluster-id");

    // when
    final var result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();

    final var errors = result.getErrors();
    assertThat(errors).hasSize(1);

    final var error = errors.iterator().next();
    assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
    assertThat(error.getDescription())
        .contains("Token claims cluster id")
        .contains("expected " + clusterId);
  }

  private static Jwt createJwtWithCluster(final String clusterId) {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        Map.of(CLUSTER_CLAIM_KEY, clusterId));
  }

  private static Jwt createJwtWithoutClusterClaim() {
    return new Jwt(
        "token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "RS256"),
        Map.of("sub", "user"));
  }
}
