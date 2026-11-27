/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.authentication.config.ClusterValidator.CLUSTER_CLAIM_KEY;
import static io.camunda.authentication.config.OrganizationValidator.ORGANIZATION_CLAIM_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.config.SaasConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;

final class SaasTokenValidatorsTest {

  private static final String ORGANIZATION_ID = "own-org";
  private static final String CLUSTER_ID = "own-cluster";

  @Test
  void shouldRejectTokenOfAnotherCluster() {
    // given
    final var validator = new DelegatingOAuth2TokenValidator<>(SaasTokenValidators.of(saas()));

    // when
    final var result = validator.validate(jwt(Map.of(CLUSTER_CLAIM_KEY, "other-cluster")));

    // then
    assertThat(result.getErrors())
        .extracting(OAuth2Error::getDescription)
        .containsExactly("Token claims cluster id other-cluster, expected own-cluster");
  }

  @Test
  void shouldRejectTokenOfAnotherOrganization() {
    // given
    final var validator = new DelegatingOAuth2TokenValidator<>(SaasTokenValidators.of(saas()));

    // when
    final var result =
        validator.validate(jwt(Map.of(ORGANIZATION_CLAIM_KEY, List.of(Map.of("id", "other-org")))));

    // then
    assertThat(result.getErrors())
        .extracting(OAuth2Error::getDescription)
        .singleElement()
        .asString()
        .startsWith("Token claims organizations");
  }

  @Test
  void shouldAcceptTokenOfOwnOrganizationAndCluster() {
    // given
    final var validator = new DelegatingOAuth2TokenValidator<>(SaasTokenValidators.of(saas()));

    // when
    final var result =
        validator.validate(
            jwt(
                Map.of(
                    CLUSTER_CLAIM_KEY,
                    CLUSTER_ID,
                    ORGANIZATION_CLAIM_KEY,
                    List.of(Map.of("id", ORGANIZATION_ID)))));

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldAcceptTokenWithoutOrganizationAndClusterClaims() {
    // given
    final var validator = new DelegatingOAuth2TokenValidator<>(SaasTokenValidators.of(saas()));

    // when
    final var result = validator.validate(jwt(Map.of()));

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldReturnNoValidatorsWhenSaasIsNotConfigured() {
    // when
    final var validators = SaasTokenValidators.of(new SaasConfiguration());

    // then
    assertThat(validators).isEmpty();
  }

  @Test
  void shouldRejectHalfConfiguredSaas() {
    // given
    final var saas = new SaasConfiguration();
    saas.setClusterId(CLUSTER_ID);

    // when / then
    assertThatThrownBy(() -> SaasTokenValidators.of(saas))
        .isInstanceOf(IllegalStateException.class);
  }

  private static SaasConfiguration saas() {
    final var saas = new SaasConfiguration();
    saas.setOrganizationId(ORGANIZATION_ID);
    saas.setClusterId(CLUSTER_ID);
    return saas;
  }

  private static Jwt jwt(final Map<String, Object> claims) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("sub", "client")
        .claims(c -> c.putAll(claims))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
  }
}
