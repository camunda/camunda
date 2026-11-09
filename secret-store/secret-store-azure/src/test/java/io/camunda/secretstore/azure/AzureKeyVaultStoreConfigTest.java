/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.azure.AzureKeyVaultStoreConfig.Mode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AzureKeyVaultStoreConfigTest {

  @Test
  void shouldDefaultEverySettingButTheVaultUrl() {
    // when
    final var config = AzureKeyVaultStoreConfig.of("https://my-vault.vault.azure.net");

    // then
    assertThat(config.vaultUrl()).isEqualTo("https://my-vault.vault.azure.net");
    assertThat(config.mode()).isEqualTo(Mode.ONE_BY_ONE);
    assertThat(config.pathPrefix()).isNull();
    assertThat(config.containerSecret()).isNull();
    assertThat(config.managedIdentityClientId()).isNull();
    assertThat(config.authorityHost()).isNull();
    assertThat(config.maxRetries()).isEqualTo(3);
    assertThat(config.callTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(config.attemptTimeout()).isEqualTo(Duration.ofSeconds(2));
  }
}
