/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class OidcPrincipalLoaderTest {

  @Test
  void shouldIgnoreMissingConfig() {
    // given
    final var claims =
        Map.<String, Object>of(
            "username", "testuser",
            "client_id", "testclient",
            "other_claim", "other_value");

    final var loader = new OidcPrincipalLoader(null, null);

    // when
    final var principals = loader.load(claims);

    // then
    assertThat(principals.username()).isNull();
    assertThat(principals.clientId()).isNull();
  }

  @Test
  void shouldLoadUsernameAndClientIdWithSimpleExpression() {
    // given
    final var claims =
        Map.<String, Object>of(
            "username", "testuser",
            "client_id", "testclient",
            "other_claim", "other_value");

    final var loader = new OidcPrincipalLoader("username", "client_id");

    // when
    final var principals = loader.load(claims);

    // then
    assertThat(principals.username()).isEqualTo("testuser");
    assertThat(principals.clientId()).isEqualTo("testclient");
  }

  @Test
  void shouldLoadUsernameAndClientId() {
    // given
    final var claims =
        Map.<String, Object>of(
            "username", "testuser",
            "client_id", "testclient",
            "other_claim", "other_value");

    final var loader = new OidcPrincipalLoader("$.username", "$.client_id");

    // when
    final var principals = loader.load(claims);

    // then
    assertThat(principals.username()).isEqualTo("testuser");
    assertThat(principals.clientId()).isEqualTo("testclient");
  }

  @Test
  void shouldLoadJustUsername() {
    // given
    final var claims =
        Map.<String, Object>of(
            "username", "testuser",
            "other_claim", "other_value");

    final var loader = new OidcPrincipalLoader("$.username", "$.client_id");

    // when
    final var principals = loader.load(claims);

    // then
    assertThat(principals.username()).isEqualTo("testuser");
    assertThat(principals.clientId()).isNull();
  }

  @Test
  void shouldLoadJustClientId() {
    // given
    final var claims =
        Map.<String, Object>of(
            "client_id", "testclient",
            "other_claim", "other_value");

    final var loader = new OidcPrincipalLoader("$.username", "$.client_id");

    // when
    final var principals = loader.load(claims);

    // then
    assertThat(principals.username()).isNull();
    assertThat(principals.clientId()).isEqualTo("testclient");
  }

  @Test
  void shouldLoadNestedUsernameAndClientId() {
    // given
    final var claims =
        Map.<String, Object>of(
            "claims",
            Map.of(
                "username", "testuser",
                "client_id", "testclient",
                "other_claim", "other_value"));

    final var loader = new OidcPrincipalLoader("$.claims.username", "$.claims.client_id");

    // when
    final var principals = loader.load(claims);

    // then
    assertThat(principals.username()).isEqualTo("testuser");
    assertThat(principals.clientId()).isEqualTo("testclient");
  }
}
