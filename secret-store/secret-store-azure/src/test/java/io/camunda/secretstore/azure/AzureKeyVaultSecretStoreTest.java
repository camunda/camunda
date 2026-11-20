/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.util.Configuration;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretStoreMetricsDoc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class AzureKeyVaultSecretStoreTest {

  private static final String VAULT_URL = "https://my-vault.vault.azure.net";

  @Test
  void shouldFanOutOneNamePerCallByDefault() {
    // when
    final var store =
        AzureKeyVaultSecretStore.fromConfig(
            "default", AzureKeyVaultStoreConfig.of(VAULT_URL), new SimpleMeterRegistry());

    // then
    assertThat(store.namesPerCall()).isEqualTo(1);
  }

  @Test
  void shouldCoverEveryNameInOneCallWithAContainerSecret() {
    // given
    final var config =
        new AzureKeyVaultStoreConfig(
            VAULT_URL,
            null,
            "camunda-secrets",
            null,
            null,
            AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
            AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT,
            AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);

    // when
    final var store =
        AzureKeyVaultSecretStore.fromConfig("default", config, new SimpleMeterRegistry());

    // then
    assertThat(store.namesPerCall()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldReportEveryNameAsNotFoundUntilResolutionIsImplemented() {
    // given
    final var store =
        AzureKeyVaultSecretStore.fromConfig(
            "default", AzureKeyVaultStoreConfig.of(VAULT_URL), new SimpleMeterRegistry());

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

  @Test
  void shouldKeepReadingTheVaultPastTheTokenLifetime() {
    // given — every token is already expired when it is issued, so each call outlives the last
    // token, as a cluster does that runs longer than one token lifetime
    final var http = StubAzureHttpClient.signingIn(0);
    final var environment =
        StubAzureHttpClient.environment(
            Map.of(
                "AZURE_TENANT_ID", "tenant-id",
                "AZURE_CLIENT_ID", "sp-client-id",
                "AZURE_CLIENT_SECRET", "sp-secret"));
    try (final var store =
        AzureKeyVaultSecretStore.fromConfig(
            "default",
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            new SimpleMeterRegistry(),
            environment,
            http)) {

      // when
      final var first = store.client().getSecret("a");
      final var second = store.client().getSecret("a");

      // then — the vault client's bearer-token policy signed in again rather than failing
      assertThat(first.getValue()).isEqualTo("secret-value");
      assertThat(second.getValue()).isEqualTo("secret-value");
      assertThat(http.tokenRequests()).hasSize(2);
    }
  }

  @Test
  void shouldShutDownTheCredentialExecutorOnClose() {
    // given
    final var store =
        AzureKeyVaultSecretStore.fromConfig(
            "default",
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            new SimpleMeterRegistry(),
            StubAzureHttpClient.environment(Map.of()),
            StubAzureHttpClient.offline());

    // when
    store.close();

    // then
    assertThat(store.credentialExecutor().isShutdown()).isTrue();
  }

  @Test
  void shouldCountAFailedRefreshWhileTheTokenIsStillValid() {
    // given a token that is still valid but inside the window the vault client refreshes it ahead
    // of its expiry
    final var http = StubAzureHttpClient.signingIn(120);
    final var meterRegistry = new SimpleMeterRegistry();
    try (final var store =
        AzureKeyVaultSecretStore.fromConfig(
            "default",
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            meterRegistry,
            servicePrincipal(),
            http)) {
      store.client().getSecret("a");

      // when every refresh is rejected
      http.rejectSignIn(true);

      // then — the client waits 30s after a token before it tries to refresh it, and from then on
      // keeps reading the vault with the old token, yet the failed refresh is counted
      Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                assertThat(store.client().getSecret("a").getValue()).isEqualTo("secret-value");
                assertThat(
                        meterRegistry
                            .get(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName())
                            .counter()
                            .count())
                    .isPositive();
              });
    }
  }

  @Test
  void shouldRemoveItsMeterOnClose() {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final var store =
        AzureKeyVaultSecretStore.fromConfig(
            "default", AzureKeyVaultStoreConfig.of(VAULT_URL), meterRegistry);
    assertThat(meterRegistry.find(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName()).counter())
        .isNotNull();

    // when
    store.close();

    // then — a store rebuilt under the same ID registers a fresh series instead of a stale one
    assertThat(meterRegistry.find(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName()).meters())
        .isEmpty();
  }

  private static Configuration servicePrincipal() {
    return StubAzureHttpClient.environment(
        Map.of(
            "AZURE_TENANT_ID", "tenant-id",
            "AZURE_CLIENT_ID", "sp-client-id",
            "AZURE_CLIENT_SECRET", "sp-secret"));
  }
}
