/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.config.OidcConfig;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OidcConfigTest {

  @Test
  void isGroupsClaimConfiguredReturnsFalseForNull() {
    final var config = createConfig(null);
    assertThat(config.isGroupsClaimConfigured()).isFalse();
  }

  @Test
  void isGroupsClaimConfiguredReturnsFalseForBlank() {
    final var config = createConfig("   ");
    assertThat(config.isGroupsClaimConfigured()).isFalse();
  }

  @Test
  void isGroupsClaimConfiguredReturnsTrueForValidClaim() {
    final var config = createConfig("groups");
    assertThat(config.isGroupsClaimConfigured()).isTrue();
  }

  @Test
  void nullListsAreNormalizedToEmpty() {
    final var config =
        new OidcConfig(
            null, null, null, null, null, null, null, null, null, null, null, null, false, null,
            null, null, null, false, null, null, null, null);

    assertThat(config.additionalJwkSetUris()).isEmpty();
    assertThat(config.audiences()).isEmpty();
  }

  private static OidcConfig createConfig(final String groupsClaim) {
    return new OidcConfig(
        "https://issuer",
        "client-id",
        "secret",
        "https://jwks",
        List.of(),
        "https://auth",
        "https://token",
        "https://logout",
        null,
        "sub",
        null,
        groupsClaim,
        false,
        "openid profile",
        List.of(),
        null,
        Duration.ofSeconds(60),
        true,
        "authorization_code",
        "client_secret_basic",
        "camunda",
        null);
  }
}
