/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.azure.AzureKeyVaultStoreConfig.Mode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AzureKeyVaultSecretStoreTest {

  private static final String VAULT_URL = "https://my-vault.vault.azure.net";

  @Test
  void shouldFanOutOneNamePerCallInOneByOneMode() {
    // when
    final var store = AzureKeyVaultSecretStore.fromConfig(AzureKeyVaultStoreConfig.of(VAULT_URL));

    // then
    assertThat(store.namesPerCall()).isEqualTo(1);
  }

  @Test
  void shouldCoverEveryNameInOneCallInContainerMode() {
    // given
    final var config =
        new AzureKeyVaultStoreConfig(
            VAULT_URL,
            null,
            Mode.CONTAINER,
            "camunda-secrets",
            null,
            null,
            AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
            AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT,
            AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);

    // when
    final var store = AzureKeyVaultSecretStore.fromConfig(config);

    // then
    assertThat(store.namesPerCall()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldReportEveryNameAsNotFoundUntilResolutionIsImplemented() {
    // given
    final var store = AzureKeyVaultSecretStore.fromConfig(AzureKeyVaultStoreConfig.of(VAULT_URL));

    // when
    final var results = store.resolve(Set.of("a", "b"));

    // then
    assertThat(results).containsOnlyKeys("a", "b");
    assertThat(results.values())
        .allSatisfy(
            result ->
                assertThat(result)
                    .isInstanceOfSatisfying(
                        Failed.class,
                        failed -> assertThat(failed.code()).isEqualTo(SecretErrorCode.NOT_FOUND)));
    assertThat(store.list()).isEmpty();
  }
}
