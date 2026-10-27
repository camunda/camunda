/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ClusterAdminOidcPropertiesTest {

  private static final String CLIENT_ID_CLAIM = "client_id";
  private static final String GROUPS_CLAIM = "groups";

  @Test
  void shouldReturnEmptyWhenNoOidcConfigured() {
    // given
    final var env = env(Map.of());

    // when
    final var props =
        ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, GROUPS_CLAIM);

    // then
    assertThat(props.clients()).isEmpty();
    assertThat(props.groups()).isEmpty();
    assertThat(props.claims()).isEmpty();
  }

  @Test
  void shouldBindClientsGroupsAndClaims() {
    // given
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.oidc.clients[0]", "ops-client",
                "camunda.security.cluster-admin.oidc.groups[0]", "camunda-admins",
                "camunda.security.cluster-admin.oidc.claims[0].name", "roles",
                "camunda.security.cluster-admin.oidc.claims[0].value", "cluster-admin"));

    // when
    final var props =
        ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, GROUPS_CLAIM);

    // then
    assertThat(props.clients()).containsExactly("ops-client");
    assertThat(props.groups()).containsExactly("camunda-admins");
    assertThat(props.claims()).containsExactly(new ClusterAdminClaim("roles", "cluster-admin"));
  }

  @Test
  void shouldRejectBlankClient() {
    // given
    final var env = env(Map.of("camunda.security.cluster-admin.oidc.clients[0]", " "));

    // when/then
    assertThatThrownBy(
            () -> ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, GROUPS_CLAIM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("blank client");
  }

  @Test
  void shouldRejectBlankGroup() {
    // given
    final var env = env(Map.of("camunda.security.cluster-admin.oidc.groups[0]", " "));

    // when/then
    assertThatThrownBy(
            () -> ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, GROUPS_CLAIM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("blank group");
  }

  @Test
  void shouldRejectBlankClaimName() {
    // given
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.oidc.claims[0].name", " ",
                "camunda.security.cluster-admin.oidc.claims[0].value", "x"));

    // when/then
    assertThatThrownBy(
            () -> ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, GROUPS_CLAIM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("name");
  }

  @Test
  void shouldRejectBlankClaimValue() {
    // given
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.oidc.claims[0].name", "roles",
                "camunda.security.cluster-admin.oidc.claims[0].value", ""));

    // when/then
    assertThatThrownBy(
            () -> ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, GROUPS_CLAIM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("value")
        .hasMessageContaining("roles");
  }

  @Test
  void shouldRejectClientsWhenProviderHasNoClientIdClaim() {
    // given — clients configured but the provider's client-id-claim is unset
    final var env = env(Map.of("camunda.security.cluster-admin.oidc.clients[0]", "ops-client"));

    // when/then
    assertThatThrownBy(() -> ClusterAdminOidcProperties.loadAndValidate(env, null, GROUPS_CLAIM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("client-id-claim");
  }

  @Test
  void shouldRejectGroupsWhenProviderHasNoGroupsClaim() {
    // given — groups configured but the provider's groups-claim is unset
    final var env = env(Map.of("camunda.security.cluster-admin.oidc.groups[0]", "camunda-admins"));

    // when/then
    assertThatThrownBy(() -> ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("groups-claim");
  }

  @Test
  void shouldAllowGenericClaimsWithoutProviderClaimPreconditions() {
    // given — only generic claims; no client/group matchers, so provider claim names are irrelevant
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.oidc.claims[0].name", "roles",
                "camunda.security.cluster-admin.oidc.claims[0].value", "cluster-admin"));

    // when
    final var props = ClusterAdminOidcProperties.loadAndValidate(env, null, null);

    // then
    assertThat(props.claims()).containsExactly(new ClusterAdminClaim("roles", "cluster-admin"));
  }

  @Test
  void shouldAcceptDuplicateClaimNamesWithDifferentValues() {
    // given — the same claim name with two values is a legitimate "matches admin OR ops" config,
    // unlike a unique key; it must not be rejected
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.oidc.claims[0].name", "roles",
                "camunda.security.cluster-admin.oidc.claims[0].value", "admin",
                "camunda.security.cluster-admin.oidc.claims[1].name", "roles",
                "camunda.security.cluster-admin.oidc.claims[1].value", "ops"));

    // when
    final var props = ClusterAdminOidcProperties.loadAndValidate(env, null, null);

    // then
    assertThat(props.claims())
        .containsExactly(
            new ClusterAdminClaim("roles", "admin"), new ClusterAdminClaim("roles", "ops"));
  }

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(env::setProperty);
    return env;
  }
}
