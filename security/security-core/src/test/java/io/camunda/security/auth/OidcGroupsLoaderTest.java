/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OidcGroupsLoaderTest {

  @Test
  void shouldIgnoreMissingConfig() {
    // given
    final var claims =
        Map.<String, Object>of(
            "groups", "testuser",
            "other_claim", "other_value");

    final var loader = new OidcGroupsLoader(null);

    // when
    final var groups = loader.load(claims);

    // then
    assertThat(groups).isNull();
  }

  @Test
  void shouldHandleEmptyRegexAsNull() {
    final var loader = new OidcGroupsLoader("");
    assertThat(loader.getGroupsClaim()).isNull();
  }

  @Test
  void shouldSanitizeInvalidRegex() {
    final var loader = new OidcGroupsLoader(".gg.ee...");
    assertThat(loader.getGroupsClaim()).isEqualTo("$['.gg.ee...']");
  }

  @Test
  void shouldThrowOnUnexpectedClaimType() {
    // given
    final var claims = Map.<String, Object>of("group", Map.of("names", List.of("g1", "g2")));
    final var loader = new OidcGroupsLoader("$.group");
    // when
    assertThatThrownBy(() -> loader.load(claims))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Group's list derived from ($.group) is not a string array. Please check your OIDC configuration.");
  }

  @Test
  void shouldLoadGroupsWithSpecialCharsExpression() {
    // given
    final var claims =
        Map.<String, Object>of(
            "http://test.com/groups", List.of("g1", "g2"), "other_claim", "other_value");

    final var loader = new OidcGroupsLoader("http://test.com/groups");

    // when
    final var groups = loader.load(claims);

    // then
    assertThat(groups).containsExactlyInAnyOrder("g1", "g2");
  }

  @Test
  void shouldLoadGroupsWithSimpleExpression() {
    // given
    final var claims =
        Map.<String, Object>of("groups", List.of("g1", "g2"), "other_claim", "other_value");

    final var loader = new OidcGroupsLoader("groups");

    // when
    final var groups = loader.load(claims);

    // then
    assertThat(groups).containsExactlyInAnyOrder("g1", "g2");
  }

  @Test
  void shouldLoadGroupsWithNestedExpression() {
    // given
    final var claims =
        Map.<String, Object>of(
            "groups",
            List.of(Map.of("name", "g1", "id", "g1id"), Map.of("name", "g2", "id", "g2id")));

    final var loader = new OidcGroupsLoader("$.groups[*].name");

    // when
    final var groups = loader.load(claims);

    // then
    assertThat(groups).containsExactlyInAnyOrder("g1", "g2");
  }

  @Test
  void shouldLoadGroupsWithStringValue() {
    // given
    final var claims = Map.<String, Object>of("group", Map.of("name", "g1", "id", "g1id"));

    final var loader = new OidcGroupsLoader("$.group.name");

    // when
    final var groups = loader.load(claims);

    // then
    assertThat(groups).containsExactlyInAnyOrder("g1");
  }

  @Test
  void shouldLoadEmptyGroupsWithNonExistentClaim() {
    // given
    final var claims =
        Map.<String, Object>of("groups", List.of("g1", "g2"), "other_claim", "other_value");

    final var loader = new OidcGroupsLoader("$.groups.names[*]");

    // when
    final var groups = loader.load(claims);

    // then
    assertThat(groups).isEmpty();
  }
}
