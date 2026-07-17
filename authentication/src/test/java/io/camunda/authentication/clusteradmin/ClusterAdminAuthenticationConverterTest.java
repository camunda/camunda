/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static io.camunda.authentication.clusteradmin.ClusterAdminSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ClusterAdminAuthenticationConverterTest {

  private final ClusterAdminAuthenticationConverter converter =
      new ClusterAdminAuthenticationConverter();

  @Test
  void shouldSupportAuthenticatedClusterAdminPrincipal() {
    // given
    final var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            "admin", "n/a", List.of(new SimpleGrantedAuthority(CLUSTER_ADMIN_AUTHORITY)));

    // when/then
    assertThat(converter.supports(authentication)).isTrue();
  }

  @Test
  void shouldNotSupportPrincipalWithoutClusterAdminAuthority() {
    // given — a plain authenticated username/password token, as the DB-backed chain produces
    final var authentication =
        UsernamePasswordAuthenticationToken.authenticated("demo", "n/a", List.of());

    // when/then
    assertThat(converter.supports(authentication))
        .as("only principals carrying the cluster-admin marker authority must be claimed")
        .isFalse();
  }

  @Test
  void shouldNotSupportPrincipalWithDifferentAuthority() {
    // given — authenticated, but with an authority other than the cluster-admin marker
    final var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            "someone", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));

    // when/then
    assertThat(converter.supports(authentication))
        .as("a non-cluster-admin authority must not be claimed")
        .isFalse();
  }

  @Test
  void shouldNotSupportUnauthenticatedClusterAdminToken() {
    // given — carries the marker authority but is not authenticated
    final var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            "admin", "n/a", List.of(new SimpleGrantedAuthority(CLUSTER_ADMIN_AUTHORITY)));
    authentication.setAuthenticated(false);

    // when/then
    assertThat(converter.supports(authentication))
        .as("an unauthenticated token must not be claimed even with the marker authority")
        .isFalse();
  }

  @Test
  void shouldNotSupportNullAuthentication() {
    // when/then
    assertThat(converter.supports(null)).isFalse();
  }

  @Test
  void shouldConvertToUsernameOnlyAuthenticationWithoutMembership() {
    // given
    final var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            "admin", "n/a", List.of(new SimpleGrantedAuthority(CLUSTER_ADMIN_AUTHORITY)));

    // when
    final CamundaAuthentication result = converter.convert(authentication);

    // then — username carried through, no groups/roles/tenants resolved
    assertThat(result.authenticatedUsername()).isEqualTo("admin");
    assertThat(result.authenticatedClientId()).isNull();
    assertThat(result.authenticatedGroupIds()).isEmpty();
    assertThat(result.authenticatedRoleIds()).isEmpty();
    assertThat(result.authenticatedTenantIds()).isEmpty();
  }
}
