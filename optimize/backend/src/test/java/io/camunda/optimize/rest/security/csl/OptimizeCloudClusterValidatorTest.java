/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OptimizeCloudClusterValidatorTest {

  private final OptimizeCloudClusterValidator validator =
      new OptimizeCloudClusterValidator("cluster-1");

  @Test
  void shouldAcceptWhenClusterIdMatches() {
    assertThat(validator.validate(jwtWithClusterId("cluster-1")).hasErrors()).isFalse();
  }

  @Test
  void shouldRejectWhenClusterIdDiffers() {
    assertThat(validator.validate(jwtWithClusterId("other-cluster")).hasErrors()).isTrue();
  }

  @Test
  void shouldAcceptWhenClusterIdClaimAbsent() {
    // Lenient on absence (OC baseline): tokens without a cluster id pass, e.g. the login id_token.
    assertThat(validator.validate(jwtWithoutClusterId()).hasErrors()).isFalse();
  }

  private static Jwt jwtWithClusterId(final String clusterId) {
    return baseJwt().claim(OptimizeCloudClusterValidator.CLUSTER_CLAIM, clusterId).build();
  }

  private static Jwt jwtWithoutClusterId() {
    return baseJwt().build();
  }

  private static Jwt.Builder baseJwt() {
    final Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .subject("user");
  }
}
