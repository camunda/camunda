/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.auth.OidcPrincipalLoader;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OidcPrincipalLoaderTest {

  @Test
  void shouldLoadUsernameAndClientIdFromSimpleClaims() {
    final var loader = new OidcPrincipalLoader("sub", "azp");
    final var claims = Map.<String, Object>of("sub", "alice", "azp", "my-client");

    final var result = loader.load(claims);

    assertThat(result.username()).isEqualTo("alice");
    assertThat(result.clientId()).isEqualTo("my-client");
  }

  @Test
  void shouldLoadFromJsonPathExpressions() {
    final var loader = new OidcPrincipalLoader("$.user.name", "$.client.id");
    final var claims =
        Map.<String, Object>of("user", Map.of("name", "bob"), "client", Map.of("id", "app-1"));

    final var result = loader.load(claims);

    assertThat(result.username()).isEqualTo("bob");
    assertThat(result.clientId()).isEqualTo("app-1");
  }

  @Test
  void shouldReturnNullUsernameWhenClaimMissing() {
    final var loader = new OidcPrincipalLoader("sub", "azp");
    final var claims = Map.<String, Object>of("azp", "my-client");

    final var result = loader.load(claims);

    assertThat(result.username()).isNull();
    assertThat(result.clientId()).isEqualTo("my-client");
  }

  @Test
  void shouldReturnNullClientIdWhenClaimMissing() {
    final var loader = new OidcPrincipalLoader("sub", "azp");
    final var claims = Map.<String, Object>of("sub", "alice");

    final var result = loader.load(claims);

    assertThat(result.username()).isEqualTo("alice");
    assertThat(result.clientId()).isNull();
  }

  @Test
  void shouldHandleBothClaimsPresent() {
    final var loader = new OidcPrincipalLoader("preferred_username", "client_id");
    final var claims =
        Map.<String, Object>of("preferred_username", "charlie", "client_id", "web-app");

    final var result = loader.load(claims);

    assertThat(result.username()).isEqualTo("charlie");
    assertThat(result.clientId()).isEqualTo("web-app");
  }

  @Test
  void shouldHandleNullClaimConfigurations() {
    final var loader = new OidcPrincipalLoader(null, null);
    final var claims = Map.<String, Object>of("sub", "alice");

    final var result = loader.load(claims);

    assertThat(result.username()).isNull();
    assertThat(result.clientId()).isNull();
  }
}
