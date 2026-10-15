/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.secretstore.hashicorpvault.HashiCorpVaultStoreConfig.AuthMethod;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class HashiCorpVaultStoreConfigTest {

  @Test
  void shouldAcceptAppRoleWithSecretIdFile() {
    assertThatCode(() -> appRole("https://vault:8200", false, "secret", "/run/secret-id", null))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRefusePlainHttpUnlessAllowed() {
    assertThatThrownBy(() -> appRole("http://vault:8200", false, "secret", "/run/id", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("https");
    assertThatCode(() -> appRole("http://vault:8200", true, "secret", "/run/id", null))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRequireExactlyOneSecretIdSource() {
    assertThatThrownBy(() -> appRole("https://vault:8200", false, "secret", null, null))
        .hasMessageContaining("exactly one of secretIdFile or secretIdEnv");
    assertThatThrownBy(() -> appRole("https://vault:8200", false, "secret", "/run/id", "ENV"))
        .hasMessageContaining("exactly one of secretIdFile or secretIdEnv");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/secret", "secret/", "kv//team", "kv/../other", "./kv", "kv?x"})
  void shouldRefuseMountsThatAreNotPlainRelativePaths(final String mount) {
    assertThatThrownBy(() -> appRole("https://vault:8200", false, mount, "/run/id", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mount");
  }

  @Test
  void shouldRequireRoleForKubernetes() {
    assertThatThrownBy(
            () ->
                new HashiCorpVaultStoreConfig(
                    URI.create("https://vault:8200"),
                    null,
                    2,
                    "secret",
                    null,
                    null,
                    "value",
                    AuthMethod.KUBERNETES,
                    "kubernetes",
                    null,
                    null,
                    null,
                    HashiCorpVaultStoreConfig.DEFAULT_KUBERNETES_JWT_PATH,
                    null,
                    null,
                    null,
                    false,
                    Duration.ofSeconds(5)))
        .hasMessageContaining("role");
  }

  @Test
  void shouldBuildEntryPathFromPrefix() {
    final var config = appRole("https://vault:8200", false, "kv/team-a", "/run/id", null);
    assertThat(config.entryPath("db")).isEqualTo("db");
  }

  private static HashiCorpVaultStoreConfig appRole(
      final String address,
      final boolean allowInsecure,
      final String mount,
      final @Nullable String secretIdFile,
      final @Nullable String secretIdEnv) {
    return new HashiCorpVaultStoreConfig(
        URI.create(address),
        null,
        2,
        mount,
        null,
        null,
        "value",
        AuthMethod.APPROLE,
        "approle",
        "role-id",
        secretIdFile,
        secretIdEnv,
        HashiCorpVaultStoreConfig.DEFAULT_KUBERNETES_JWT_PATH,
        null,
        null,
        null,
        allowInsecure,
        Duration.ofSeconds(5));
  }
}
