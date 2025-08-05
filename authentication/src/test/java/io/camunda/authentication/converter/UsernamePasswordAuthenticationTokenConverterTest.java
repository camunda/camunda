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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public class UsernamePasswordAuthenticationTokenConverterTest {

  @Mock private GroupServices groupServices;
  @Mock private RoleServices roleServices;
  @Mock private TenantServices tenantServices;
  private UsernamePasswordAuthenticationTokenConverter authenticationConverter;

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
  void shouldSupport() {
    // given
    final var authentication = mock(UsernamePasswordAuthenticationToken.class);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupport() {
    // given
    final var authentication = mock(OAuth2AuthenticationToken.class);

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

  private Authentication getUsernamePasswordAuthentication(
      final String username, final String password) {
    return new UsernamePasswordAuthenticationToken(username, password);
  }
}
