/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.oauth;

import static io.camunda.optimize.rest.security.oauth.RoleValidator.ORGANIZATION_CLAIM_KEY;
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

  private static final String ORGANIZATION_ID_1 = "org1";
  private static final String ORGANIZATION_ID_2 = "org2";
  private final List<String> allowedRoles = Arrays.asList("admin", "analyst", "owner");
  private final RoleValidator validator = new RoleValidator(allowedRoles, ORGANIZATION_ID_1);

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
    org.put("id", ORGANIZATION_ID_1);
    org.put("roles", Arrays.asList("admin", "developer"));
    claims.put(ORGANIZATION_CLAIM_KEY, List.of(org));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldSucceedWhenUserHasAllowedRoleInCorrectOrg() {
    // given - user has "analyst" in org1 (the configured org) and only disallowed roles in org2
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org1 = new HashMap<>();
    org1.put("id", ORGANIZATION_ID_1);
    org1.put("roles", List.of("analyst"));
    final Map<String, Object> org2 = new HashMap<>();
    org2.put("id", ORGANIZATION_ID_2);
    org2.put("roles", List.of("developer"));
    claims.put(ORGANIZATION_CLAIM_KEY, Arrays.asList(org1, org2));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void shouldFailWhenUserHasAllowedRoleInDifferentOrgOnly() {
    // given - user2 has "analyst" in org2 but the cluster belongs to org1 (the bug scenario)
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org1 = new HashMap<>();
    org1.put("id", ORGANIZATION_ID_1);
    org1.put("roles", List.of("developer"));
    final Map<String, Object> org2 = new HashMap<>();
    org2.put("id", ORGANIZATION_ID_2);
    org2.put("roles", List.of("analyst"));
    claims.put(ORGANIZATION_CLAIM_KEY, Arrays.asList(org1, org2));
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
  }

  @Test
  void shouldFailWhenUserHasNoAllowedRole() {
    // given
    final Jwt token = mock(Jwt.class);
    final Map<String, Object> claims = new HashMap<>();
    final Map<String, Object> org = new HashMap<>();
    org.put("id", ORGANIZATION_ID_1);
    org.put("roles", Arrays.asList("developer", "viewer"));
    claims.put(ORGANIZATION_CLAIM_KEY, List.of(org));
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
    org.put("id", ORGANIZATION_ID_1);
    org.put("roles", Collections.emptyList());
    claims.put(ORGANIZATION_CLAIM_KEY, List.of(org));
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
    claims.put(ORGANIZATION_CLAIM_KEY, Collections.emptyList());
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
    claims.put(ORGANIZATION_CLAIM_KEY, "invalid-structure");
    when(token.getClaims()).thenReturn(claims);

    // when
    final OAuth2TokenValidatorResult result = validator.validate(token);

    // then
    assertThat(result.hasErrors()).isTrue();
  }
}
