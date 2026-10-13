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

import com.azure.security.keyvault.secrets.SecretClient;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainer;
import com.github.nagyesta.lowkeyvault.testcontainers.LowkeyVaultContainerBuilder;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.time.InstantSource;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration tests that drive the real Azure {@link SecretClient} against the Lowkey
 * Vault emulator, exercising the production {@link OneByOneSecretResolver} and {@link
 * ContainerSecretResolver} paths through {@link AzureKeyVaultSecretStore}. Fine-grained error-code
 * classification is covered by {@link AzureKeyVaultSecretStoreTest}; this suite proves the wire
 * path works against a real Key Vault server.
 *
 * <p>Unlike the GCP and AWS suites, this one does NOT go through {@link
 * AzureKeyVaultSecretStore#fromConfig} to build the client. Key Vault only speaks TLS, and the
 * emulator serves a self-signed certificate, so the production {@code fromConfig} path (which
 * builds a {@code DefaultAzureCredential} and a default {@code HttpClient} that validates
 * certificates) cannot talk to it. The emulator ships a trust-configured {@link SecretClient}
 * builder instead, which this test feeds into the store's package-private test constructor. This is
 * a known emulator limitation, recorded as a PoC finding: {@code fromConfig} itself is covered by
 * unit tests.
 */
@Testcontainers
class AzureKeyVaultSecretStoreIT {

  private static final String PREFIX = "camunda-";
  private static final String CONTAINER_PREFIX = "cfg-";

  @Container
  private static final LowkeyVaultContainer VAULT =
      LowkeyVaultContainerBuilder.lowkeyVault("nagyesta/lowkey-vault:7.3.127").build();

  @BeforeAll
  static void seed() {
    final var admin =
        VAULT.getClientFactory().getSecretClientBuilderForDefaultVault().buildClient();

    admin.setSecret("camunda-db-password", "s3cr3t");
    admin.setSecret("camunda-api-token", "tok3n");
    admin.setSecret("other-unrelated", "ignored");
    admin.setSecret(
        "cfg-app-config", "{\"DB_PASSWORD\":\"c0nt41ner\",\"API_KEY\":\"k3y\",\"NUM\":42}");
    admin.setSecret("cfg-bad-json", "not json");
    admin.setSecret("cfg-arr", "[\"a\",\"b\"]");
  }

  @Test
  void shouldResolveSecret() {
    // given — the production resolver over a real server
    final var store = oneByOneStore();

    // when
    final var result = store.resolve(Set.of("db-password"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldReturnNotFoundForMissingSecret() {
    // given
    final var store = oneByOneStore();

    // when
    final var result = store.resolve(Set.of("does-not-exist"));

    // then
    assertThat(((Failed) result.get("does-not-exist")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldResolveBatchWithHitAndMiss() {
    // given
    final var store = oneByOneStore();

    // when
    final var result = store.resolve(Set.of("db-password", "api-token", "does-not-exist"));

    // then — every requested ref has a result, hits resolved and the miss failed
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
    assertThat(result.get("api-token"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("tok3n");
    assertThat(result.get("does-not-exist")).isInstanceOf(Failed.class);
  }

  @Test
  void shouldListSecretsUnderPrefixWithPrefixStripped() {
    // given
    final var store = oneByOneStore();

    // when
    final var refs = store.list();

    // then — only camunda-* secrets, prefix stripped; other-* and cfg-* excluded
    assertThat(refs).contains("db-password", "api-token").doesNotContain("other-unrelated");
  }

  @Test
  void shouldResolveMultipleKeysFromContainer() {
    // given
    final var store = containerStore("app-config");

    // when
    final var result = store.resolve(Set.of("DB_PASSWORD", "API_KEY"));

    // then
    assertThat(result.get("DB_PASSWORD"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("c0nt41ner");
    assertThat(result.get("API_KEY"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("k3y");
  }

  @Test
  void shouldReturnNotFoundForMissingKeyInContainer() {
    // given
    final var store = containerStore("app-config");

    // when
    final var result = store.resolve(Set.of("MISSING"));

    // then
    assertThat(((Failed) result.get("MISSING")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForNonStringKeyInContainer() {
    // given
    final var store = containerStore("app-config");

    // when
    final var result = store.resolve(Set.of("NUM"));

    // then
    assertThat(((Failed) result.get("NUM")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldFailAllKeysWhenContainerIsNotValidJson() {
    // given
    final var store = containerStore("bad-json");

    // when
    final var result = store.resolve(Set.of("A", "B"));

    // then
    assertThat(((Failed) result.get("A")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(((Failed) result.get("B")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldFailAllKeysWhenContainerIsNotJsonObject() {
    // given — a valid JSON array is not usable as a key/value container
    final var store = containerStore("arr");

    // when
    final var result = store.resolve(Set.of("A"));

    // then
    assertThat(((Failed) result.get("A")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldListKeysFromContainer() {
    // given
    final var store = containerStore("app-config");

    // when
    final var refs = store.list();

    // then
    assertThat(refs).containsExactlyInAnyOrder("DB_PASSWORD", "API_KEY", "NUM");
  }

  @Test
  void shouldThrowUnavailableWhenContainerListIsNotJsonObject() {
    // given
    final var store = containerStore("arr");

    // when / then — the malformed-content message must surface, not a generic "unavailable" one
    assertThatThrownBy(store::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("is not a JSON object");
  }

  private static AzureKeyVaultSecretStore oneByOneStore() {
    return new AzureKeyVaultSecretStore(trustingClient(), PREFIX);
  }

  private static AzureKeyVaultSecretStore containerStore(final String containerSecretName) {
    return new AzureKeyVaultSecretStore(
        trustingClient(), CONTAINER_PREFIX, containerSecretName, InstantSource.system());
  }

  private static SecretClient trustingClient() {
    // The emulator serves a self-signed certificate; this trust-configured builder (shipped by
    // Lowkey Vault) is used instead of the production fromConfig client, which validates the chain.
    return VAULT.getClientFactory().getSecretClientBuilderForDefaultVault().buildClient();
  }
}
