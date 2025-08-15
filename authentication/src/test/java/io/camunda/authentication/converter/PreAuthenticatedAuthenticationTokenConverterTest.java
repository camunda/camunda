/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class PreAuthenticatedAuthenticationTokenConverterTest {

  @Mock private RoleServices roleServices;
  @Mock private GroupServices groupServices;
  @Mock private TenantServices tenantServices;
  @Mock private X509Certificate certificate;
  @Mock private Jwt jwt;

  private PreAuthenticatedAuthenticationTokenConverter converter;

  @BeforeEach
  void setUp() {
    converter =
        new PreAuthenticatedAuthenticationTokenConverter(
            roleServices, groupServices, tenantServices);
  }

  @Test
  void shouldSupportPreAuthenticatedTokenWithCertificate() {
    // Given
    final var auth = new PreAuthenticatedAuthenticationToken("user", certificate);

    // When & Then
    assertThat(converter.supports(auth)).isTrue();
  }

  @Test
  void shouldNotSupportPreAuthenticatedTokenWithoutCertificate() {
    // Given
    final var auth = new PreAuthenticatedAuthenticationToken("user", "password");

    // When & Then
    assertThat(converter.supports(auth)).isFalse();
  }

  @Test
  void shouldNotSupportOtherAuthenticationTypes() {
    // Given
    final var auth = new JwtAuthenticationToken(jwt);

    // When & Then
    assertThat(converter.supports(auth)).isFalse();
  }

  @Test
  void shouldNotSupportNullAuthentication() {
    // When & Then
    assertThat(converter.supports(null)).isFalse();
  }

  @Test
  void shouldConvertAuthenticationWithUserOnly() {
    // Given
    final var auth = new PreAuthenticatedAuthenticationToken("testuser", certificate);

    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);

    when(groupServices.getGroupsByMemberTypeAndMemberIds(any())).thenReturn(List.of());
    when(roleServices.getRolesByMemberTypeAndMemberIds(any())).thenReturn(List.of());
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any())).thenReturn(List.of());

    // When
    final CamundaAuthentication result = converter.convert(auth);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.authenticatedUsername()).isEqualTo("testuser");
    assertThat(result.authenticatedRoleIds()).isEmpty();
    assertThat(result.authenticatedGroupIds()).isEmpty();
    assertThat(result.authenticatedTenantIds()).isEmpty();
  }

  @Test
  void shouldConvertAuthenticationWithGroupsAndRoles() {
    // Given
    final var auth = new PreAuthenticatedAuthenticationToken("testuser", certificate);

    final var group1 = new GroupEntity(1L, "group1", "Group 1", null);
    final var role1 = new RoleEntity(1L, "role1", "Role 1", null);
    final var tenant1 = new TenantEntity(1L, "tenant1", "Tenant 1", null);

    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);

    when(groupServices.getGroupsByMemberTypeAndMemberIds(any())).thenReturn(List.of(group1));
    when(roleServices.getRolesByMemberTypeAndMemberIds(any())).thenReturn(List.of(role1));
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any())).thenReturn(List.of(tenant1));

    // When
    final CamundaAuthentication result = converter.convert(auth);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.authenticatedUsername()).isEqualTo("testuser");
    assertThat(result.authenticatedRoleIds()).containsExactly("role1");
    assertThat(result.authenticatedGroupIds()).containsExactly("group1");
    assertThat(result.authenticatedTenantIds()).containsExactly("tenant1");
  }

  @Test
  void shouldIncludeAuthorizedUsernameClaim() {
    // Given
    final var auth = new PreAuthenticatedAuthenticationToken("testuser", certificate);

    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);

    when(groupServices.getGroupsByMemberTypeAndMemberIds(any())).thenReturn(List.of());
    when(roleServices.getRolesByMemberTypeAndMemberIds(any())).thenReturn(List.of());
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any())).thenReturn(List.of());

    // When
    final CamundaAuthentication result = converter.convert(auth);

    // Then
    assertThat(result.claims()).containsEntry("authorized_username", "testuser");
  }
}
