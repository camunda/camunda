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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

final class SaasTokenValidatorsTest {

  private static final String ORGANIZATION_ID = "own-org";
  private static final String CLUSTER_ID = "own-cluster";

  @Test
  void shouldRejectTokenOfAnotherCluster() {
    // given
    final var decoder =
        SaasTokenValidators.withSaasValidation(
            decoderReturning(Map.of(CLUSTER_CLAIM_KEY, "other-cluster")), saas());

    // when / then
    assertThatThrownBy(() -> decoder.decode("token"))
        .isInstanceOf(JwtValidationException.class)
        .hasMessage("Token claims cluster id other-cluster, expected own-cluster");
  }

  @Test
  void shouldRejectTokenOfAnotherOrganization() {
    // given
    final var decoder =
        SaasTokenValidators.withSaasValidation(
            decoderReturning(Map.of(ORGANIZATION_CLAIM_KEY, List.of(Map.of("id", "other-org")))),
            saas());

    // when / then
    assertThatThrownBy(() -> decoder.decode("token"))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageStartingWith("Token claims organizations");
  }

  @Test
  void shouldAcceptTokenOfOwnOrganizationAndCluster() {
    // given
    final var claims =
        Map.<String, Object>of(
            CLUSTER_CLAIM_KEY,
            CLUSTER_ID,
            ORGANIZATION_CLAIM_KEY,
            List.of(Map.of("id", ORGANIZATION_ID)));
    final var decoder = SaasTokenValidators.withSaasValidation(decoderReturning(claims), saas());

    // when
    final var jwt = decoder.decode("token");

    // then
    assertThat(jwt.getClaims()).containsAllEntriesOf(claims);
  }

  @Test
  void shouldPropagateDecoderFailure() {
    // given
    final JwtDecoder failing =
        token -> {
          throw new JwtException("bad signature");
        };
    final var decoder = SaasTokenValidators.withSaasValidation(failing, saas());

    // when / then
    assertThatThrownBy(() -> decoder.decode("token")).hasMessage("bad signature");
  }

  @Test
  void shouldReturnSameDecoderWhenSaasIsNotConfigured() {
    // given
    final var delegate = decoderReturning(Map.of(CLUSTER_CLAIM_KEY, "other-cluster"));

    // when
    final var decoder = SaasTokenValidators.withSaasValidation(delegate, new SaasConfiguration());

    // then
    assertThat(decoder).isSameAs(delegate);
  }

  @Test
  void shouldRejectHalfConfiguredSaas() {
    // given
    final var saas = new SaasConfiguration();
    saas.setClusterId(CLUSTER_ID);

    // when / then
    assertThatThrownBy(() -> SaasTokenValidators.withSaasValidation(token -> null, saas))
        .isInstanceOf(IllegalStateException.class);
  }

  private static SaasConfiguration saas() {
    final var saas = new SaasConfiguration();
    saas.setOrganizationId(ORGANIZATION_ID);
    saas.setClusterId(CLUSTER_ID);
    return saas;
  }

  private static JwtDecoder decoderReturning(final Map<String, Object> claims) {
    return token ->
        Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .claim("sub", "client")
            .claims(c -> c.putAll(claims))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
  }
}
