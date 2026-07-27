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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OptimizeCloudOrganizationValidatorTest {

  private final OptimizeCloudOrganizationValidator validator =
      new OptimizeCloudOrganizationValidator("org-1");

  @Test
  void shouldAcceptWhenMemberOfConfiguredOrg() {
    final Jwt token = jwtWithOrgs(List.of(Map.of("id", "org-1", "roles", List.of("viewer"))));

    assertThat(validator.validate(token).hasErrors()).isFalse();
  }

  @Test
  void shouldAcceptRegardlessOfOrgRole() {
    // Aligned with OC: membership only, the Auth0 org role no longer gates access.
    final Jwt token = jwtWithOrgs(List.of(Map.of("id", "org-1", "roles", List.of())));

    assertThat(validator.validate(token).hasErrors()).isFalse();
  }

  @Test
  void shouldRejectWhenNotMemberOfConfiguredOrg() {
    final Jwt token = jwtWithOrgs(List.of(Map.of("id", "org-2", "roles", List.of("admin"))));

    assertThat(validator.validate(token).hasErrors()).isTrue();
  }

  @Test
  void shouldAcceptWhenOrganizationsClaimAbsent() {
    // Lenient on absence (OC baseline): tokens without an orgs claim pass, e.g. M2M bearer tokens.
    assertThat(validator.validate(jwtWithoutOrgs()).hasErrors()).isFalse();
  }

  @Test
  void shouldRejectWhenOrganizationsClaimIsNotACollection() {
    final Jwt token =
        baseJwt().claim(OptimizeCloudOrganizationValidator.ORGANIZATIONS_CLAIM, "org-1").build();

    assertThat(validator.validate(token).hasErrors()).isTrue();
  }

  private static Jwt jwtWithOrgs(final List<Map<String, Object>> orgs) {
    return baseJwt().claim(OptimizeCloudOrganizationValidator.ORGANIZATIONS_CLAIM, orgs).build();
  }

  private static Jwt jwtWithoutOrgs() {
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
