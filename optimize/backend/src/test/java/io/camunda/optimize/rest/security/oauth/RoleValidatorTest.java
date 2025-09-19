/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class RoleValidatorTest {

  private final List<String> allowedRoles = Arrays.asList("admin", "analyst", "owner");
  private final RoleValidator validator = new RoleValidator(allowedRoles);

  @Test
  void shouldFailWhenTokenHasNoOrganizationClaim() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void shouldSucceedWhenUserHasAllowedRole() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org = new HashMap<>();
    org.put("id", "org1");
    org.put("roles", Arrays.asList("admin", "developer"));
    claims.put("https://camunda.com/orgs", List.of(org));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldSucceedWhenUserHasAllowedRoleInAnyOrg() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org1 = new HashMap<>();
    org1.put("id", "org1");
    org1.put("roles", List.of("developer"));
    final Map<String, Object> org2 = new HashMap<>();
    org2.put("id", "org2");
    org2.put("roles", List.of("analyst"));
    claims.put("https://camunda.com/orgs", Arrays.asList(org1, org2));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldFailWhenUserHasNoAllowedRole() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org = new HashMap<>();
    org.put("id", "org1");
    org.put("roles", Arrays.asList("developer", "viewer"));
    claims.put("https://camunda.com/orgs", List.of(org));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
  }

  @Test
  void shouldFailWhenOrganizationHasNoRoles() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org = new HashMap<>();
    org.put("id", "org1");
    org.put("roles", Collections.emptyList());
    claims.put("https://camunda.com/orgs", List.of(org));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void shouldFailWhenOrganizationClaimIsEmpty() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    claims.put("https://camunda.com/orgs", Collections.emptyList());
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void shouldFailWhenOrganizationClaimHasInvalidStructure() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    claims.put("https://camunda.com/orgs", "invalid-structure");
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
  }
}
