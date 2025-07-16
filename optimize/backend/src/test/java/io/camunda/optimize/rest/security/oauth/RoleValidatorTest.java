/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

  private final List<String> allowedRoles = Arrays.asList("admin", "analyst");
  private final RoleValidator validator = new RoleValidator(allowedRoles);

  @Test
  void shouldSucceedWhenTokenHasNoOrganizationClaim() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertFalse(result.hasErrors());
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
    assertFalse(result.hasErrors());
  }

  @Test
  void shouldSucceedWhenUserHasAllowedRoleInAnyOrg() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org1 = new HashMap<>();
    org1.put("id", "org1");
    org1.put("roles", Arrays.asList("developer"));
    final Map<String, Object> org2 = new HashMap<>();
    org2.put("id", "org2");
    org2.put("roles", Arrays.asList("analyst"));
    claims.put("https://camunda.com/orgs", Arrays.asList(org1, org2));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertFalse(result.hasErrors());
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
    assertTrue(result.hasErrors());
    assertEquals("invalid_token", result.getErrors().iterator().next().getErrorCode());
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
    assertTrue(result.hasErrors());
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
    assertTrue(result.hasErrors());
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
    assertTrue(result.hasErrors());
  }

  @Test
  void shouldWorkWithCustomAllowedRoles() {
    // given
    final List<String> customRoles = Arrays.asList("owner", "manager");
    final RoleValidator customValidator = new RoleValidator(customRoles);
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org = new HashMap<>();
    org.put("id", "org1");
    org.put("roles", Arrays.asList("owner", "viewer"));
    claims.put("https://camunda.com/orgs", List.of(org));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = customValidator.validate(token);

    // then
    assertFalse(result.hasErrors());
  }
}
