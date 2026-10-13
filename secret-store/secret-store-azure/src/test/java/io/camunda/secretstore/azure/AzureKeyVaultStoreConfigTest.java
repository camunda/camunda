/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AzureKeyVaultStoreConfigTest {

  private static final String VAULT = "https://my-vault.vault.azure.net";

  @Test
  void shouldRejectNullVaultUrl() {
    // when / then
    assertThatThrownBy(() -> AzureKeyVaultStoreConfig.of(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("vaultUrl");
  }

  @Test
  void shouldRejectBlankVaultUrl() {
    // when / then
    assertThatThrownBy(() -> AzureKeyVaultStoreConfig.of(" ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("vaultUrl");
  }

  @Test
  void shouldRejectBlankContainerSecretName() {
    // when / then
    assertThatThrownBy(() -> new AzureKeyVaultStoreConfig(VAULT, null, " ", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("containerSecretName");
  }

  @Test
  void shouldRejectBlankManagedIdentityClientId() {
    // when / then
    assertThatThrownBy(() -> new AzureKeyVaultStoreConfig(VAULT, null, null, " ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("managedIdentityClientId");
  }

  @Test
  void shouldRejectNegativeMaxRetries() {
    // when / then
    assertThatThrownBy(
            () ->
                new AzureKeyVaultStoreConfig(
                    VAULT, null, null, null, null, -1, Duration.ofSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxRetries");
  }

  @Test
  void shouldRejectNonPositiveCallTimeout() {
    // when / then
    assertThatThrownBy(
            () -> new AzureKeyVaultStoreConfig(VAULT, null, null, null, null, 3, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("callTimeout");
  }

  @Test
  void shouldRejectNullCallTimeout() {
    // when / then
    assertThatThrownBy(() -> new AzureKeyVaultStoreConfig(VAULT, null, null, null, null, 3, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("callTimeout");
  }

  @Test
  void shouldDefaultOptionalFieldsInFactory() {
    // when
    final var config = AzureKeyVaultStoreConfig.of(VAULT, "camunda-");

    // then
    assertThat(config.vaultUrl()).isEqualTo(VAULT);
    assertThat(config.pathPrefix()).isEqualTo("camunda-");
    assertThat(config.containerSecretName()).isNull();
    assertThat(config.managedIdentityClientId()).isNull();
    assertThat(config.authorityHost()).isNull();
    assertThat(config.maxRetries()).isEqualTo(AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES);
    assertThat(config.callTimeout()).isEqualTo(AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT);
  }

  @Test
  void shouldDefaultRetriesAndTimeoutInConvenienceConstructor() {
    // when
    final var config = new AzureKeyVaultStoreConfig(VAULT, "camunda-", null, null, null);

    // then
    assertThat(config.maxRetries()).isEqualTo(AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES);
    assertThat(config.callTimeout()).isEqualTo(AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT);
  }
}
