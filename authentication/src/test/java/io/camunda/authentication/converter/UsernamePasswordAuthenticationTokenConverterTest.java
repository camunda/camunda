/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

public class UsernamePasswordAuthenticationTokenConverterTest {

  @Mock private GroupServices groupServices;
  @Mock private RoleServices roleServices;
  @Mock private TenantServices tenantServices;
  private CamundaAuthenticationConverter<Authentication> authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();

    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);

    authenticationConverter =
        new UsernamePasswordAuthenticationTokenConverter(
            roleServices, groupServices, tenantServices);
  }

  @Test
  void shouldSupportUsernamePasswordAuthentication() {
    // given
    final var authentication = getUsernamePasswordAuthentication("test-user", "test-password");

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupportNoneUsernamePasswordAuthentication() {
    // given
    final String usernameClaim = "sub";
    final String usernameValue = "test-user";
    final var authentication = getOidcAuthenticationInContext(usernameClaim, usernameValue, "aud1");

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isFalse();
  }

  @Test
  void authenticationContainsUsername() {
    // given
    final var authentication = getUsernamePasswordAuthentication("test-user", "test-password");

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo("test-user");
  }

  @Test
  void authenticationContainsGroups() {
    // given
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any()))
        .thenReturn(
            List.of(
                new GroupEntity(1L, "group1", "group", "desc"),
                new GroupEntity(2L, "group2", "group", "desc")));
    final var authentication = getUsernamePasswordAuthentication("test-user", "test-password");

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    assertThat(camundaAuthentication.authenticatedGroupIds())
        .containsExactlyInAnyOrder("group1", "group2");
  }

  @Test
  void authenticationContainsRoles() {
    // given
    when(roleServices.getRolesByMemberTypeAndMemberIds(any()))
        .thenReturn(
            List.of(
                new RoleEntity(1L, "role1", "role", "desc"),
                new RoleEntity(2L, "role2", "role", "desc")));
    final var authentication = getUsernamePasswordAuthentication("test-user", "test-password");

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    assertThat(camundaAuthentication.authenticatedRoleIds())
        .containsExactlyInAnyOrder("role1", "role2");
  }

  @Test
  void authenticationContainsTenants() {
    // given
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any()))
        .thenReturn(
            List.of(
                new TenantEntity(1L, "tenant1", "tenant", "desc"),
                new TenantEntity(2L, "tenant2", "tenant", "desc")));
    final var authentication = getUsernamePasswordAuthentication("test-user", "test-password");

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    assertThat(camundaAuthentication.authenticatedTenantIds())
        .containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  void authenticationContainsUsernameClaim() {
    // given
    final var authentication = getUsernamePasswordAuthentication("test-user", "test-password");

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    assertThat(camundaAuthentication.claims()).containsEntry(AUTHORIZED_USERNAME, "test-user");
  }

  private Authentication getOidcAuthenticationInContext(
      final String usernameClaim, final String usernameValue, final String aud) {
    final String tokenValue = "{}";
    final Instant tokenIssuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    final Instant tokenExpiresAt = tokenIssuedAt.plus(1, ChronoUnit.DAYS);

    return new OAuth2AuthenticationToken(
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    tokenValue,
                    tokenIssuedAt,
                    tokenExpiresAt,
                    Map.of("aud", aud, usernameClaim, usernameValue))),
            null,
            Collections.emptySet(),
            new AuthenticationContextBuilder().withUsername(usernameValue).build()),
        List.of(),
        "oidc");
  }

  private Authentication getUsernamePasswordAuthentication(
      final String username, final String password) {
    return new UsernamePasswordAuthenticationToken(username, password);
  }
}
