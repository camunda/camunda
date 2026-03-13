/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gatekeeper.auth.OidcGroupsLoader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OidcGroupsLoaderTest {

  @Test
  void shouldLoadGroupsFromSimpleClaimName() {
    final var loader = new OidcGroupsLoader("groups");
    final var claims = Map.<String, Object>of("groups", List.of("admin", "dev"));

    final var result = loader.load(claims);

    assertThat(result).containsExactly("admin", "dev");
  }

  @Test
  void shouldLoadGroupsFromJsonPathExpression() {
    final var loader = new OidcGroupsLoader("$.realm_access.roles");
    final var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of("a", "b")));

    final var result = loader.load(claims);

    assertThat(result).containsExactly("a", "b");
  }

  @Test
  void shouldHandleSingleStringGroupClaim() {
    final var loader = new OidcGroupsLoader("groups");
    final var claims = Map.<String, Object>of("groups", "single-group");

    final var result = loader.load(claims);

    assertThat(result).containsExactly("single-group");
  }

  @Test
  void shouldReturnEmptyListWhenClaimIsMissing() {
    final var loader = new OidcGroupsLoader("groups");
    final var claims = Map.<String, Object>of("other", "value");

    final var result = loader.load(claims);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenGroupsClaimIsNull() {
    final var loader = new OidcGroupsLoader(null);

    final var result = loader.load(Map.of());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenGroupsClaimIsEmpty() {
    final var loader = new OidcGroupsLoader("");

    final var result = loader.load(Map.of());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldExposeGroupsClaim() {
    final var loader = new OidcGroupsLoader("groups");

    assertThat(loader.getGroupsClaim()).isEqualTo("$['groups']");
  }

  @Test
  void shouldThrowOnInvalidJsonPath() {
    assertThatThrownBy(() -> new OidcGroupsLoader("$[invalid"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
