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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link ClusterAdminBasicAuthProperties}.
 *
 * <p>All tests bind config from a {@link MockEnvironment} — no Spring context is loaded.
 */
class ClusterAdminBasicAuthPropertiesTest {

  @Test
  void shouldReturnEmptyListWhenNoClusterAdminConfigured() {
    // given
    final var env = env(Map.of());

    // when
    final List<ClusterAdminUser> users = ClusterAdminBasicAuthProperties.loadAndValidate(env);

    // then
    assertThat(users).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenUsersListIsEmpty() {
    // given — the users key is declared but the list itself is empty
    final var env = env(Map.of("camunda.security.cluster-admin.basic.users", ""));

    // when
    final List<ClusterAdminUser> users = ClusterAdminBasicAuthProperties.loadAndValidate(env);

    // then
    assertThat(users).isEmpty();
  }

  @Test
  void shouldBindConfiguredUsers() {
    // given
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.basic.users[0].name", "admin",
                "camunda.security.cluster-admin.basic.users[0].password", "secret",
                "camunda.security.cluster-admin.basic.users[1].name", "admin2",
                "camunda.security.cluster-admin.basic.users[1].password", "secret2"));

    // when
    final List<ClusterAdminUser> users = ClusterAdminBasicAuthProperties.loadAndValidate(env);

    // then
    assertThat(users)
        .containsExactly(
            new ClusterAdminUser("admin", "secret"), new ClusterAdminUser("admin2", "secret2"));
  }

  @Test
  void shouldRejectDuplicateNames() {
    // given
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.basic.users[0].name", "admin",
                "camunda.security.cluster-admin.basic.users[0].password", "secret",
                "camunda.security.cluster-admin.basic.users[1].name", "admin",
                "camunda.security.cluster-admin.basic.users[1].password", "other-secret"));

    // when/then
    assertThatThrownBy(() -> ClusterAdminBasicAuthProperties.loadAndValidate(env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate")
        .hasMessageContaining("admin");
  }

  @Test
  void shouldRejectBlankName() {
    // given
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.basic.users[0].name", " ",
                "camunda.security.cluster-admin.basic.users[0].password", "secret"));

    // when/then
    assertThatThrownBy(() -> ClusterAdminBasicAuthProperties.loadAndValidate(env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("name");
  }

  @Test
  void shouldRejectBlankPassword() {
    // given
    final var env =
        env(
            Map.of(
                "camunda.security.cluster-admin.basic.users[0].name", "admin",
                "camunda.security.cluster-admin.basic.users[0].password", ""));

    // when/then
    assertThatThrownBy(() -> ClusterAdminBasicAuthProperties.loadAndValidate(env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("password")
        .hasMessageContaining("admin");
  }

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(env::setProperty);
    return env;
  }
}
