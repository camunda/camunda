/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.secretstore.hashicorpvault.HashiCorpVaultStoreConfig.AuthMethod;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class HashiCorpVaultSecretStoreTest {

  @TempDir Path tempDir;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private FakeHashiCorpVault hashiCorpVault;
  private Path secretIdFile;
  private HashiCorpVaultSecretStore store;

  @BeforeEach
  void setUp() throws IOException {
    hashiCorpVault = new FakeHashiCorpVault();
    secretIdFile = tempDir.resolve("secret-id");
    Files.writeString(secretIdFile, hashiCorpVault.acceptedSecretId + "\n");
  }

  @AfterEach
  void tearDown() {
    if (store != null) {
      store.close();
    }
    hashiCorpVault.close();
  }

  @Test
  void shouldResolveFieldOfEntryUnderPathPrefix() {
    // given
    hashiCorpVault.entries.put("camunda/db-password", "{\"value\":\"s3cr3t\",\"other\":\"x\"}");
    store = createStore(config("camunda/", null));

    // when
    final var results = store.resolve(Set.of("db-password"));

    // then
    assertThat(results).containsExactly(Map.entry("db-password", new Resolved("s3cr3t")));
    assertThat(store.namesPerCall()).isEqualTo(1);
  }

  @Test
  void shouldReturnNotFoundForMissingEntryOrField() {
    // given
    hashiCorpVault.entries.put("no-value-field", "{\"other\":\"x\"}");
    store = createStore(config(null, null));

    // when
    final var results = store.resolve(Set.of("missing", "no-value-field"));

    // then
    assertThat(results.values())
        .allSatisfy(
            result ->
                assertThat(result)
                    .isInstanceOfSatisfying(
                        Failed.class,
                        failed -> assertThat(failed.code()).isEqualTo(SecretErrorCode.NOT_FOUND)));
  }

  @Test
  void shouldReturnInvalidRefForNonStringField() {
    // given
    hashiCorpVault.entries.put("port", "{\"value\":5432}");
    store = createStore(config(null, null));

    // when
    final var result = store.resolve(Set.of("port")).get("port");

    // then
    assertThat(result)
        .isInstanceOfSatisfying(
            Failed.class,
            failed -> {
              assertThat(failed.code()).isEqualTo(SecretErrorCode.INVALID_REF);
              assertThat(failed.message()).doesNotContain("5432");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"..", ".", "../other-team/db", "a/b", "a?b", "a#b", "a%2Fb", "a b", ""})
  void shouldRefuseNamesThatWouldLeaveThePathPrefix(final String name) {
    // given
    store = createStore(config("camunda/", null));

    // when
    final var result = store.resolve(Set.of(name)).get(name);

    // then
    assertThat(result)
        .isInstanceOfSatisfying(
            Failed.class,
            failed -> assertThat(failed.code()).isEqualTo(SecretErrorCode.INVALID_REF));
    assertThat(hashiCorpVault.reads).hasValue(0);
  }

  @Test
  void shouldSignInAgainOnceWhenTokenWasRevoked() {
    // given
    hashiCorpVault.entries.put("db-password", "{\"value\":\"s3cr3t\"}");
    store = createStore(config(null, null));
    store.resolve(Set.of("db-password"));
    hashiCorpVault.revokeCurrentToken();

    // when
    final var results = store.resolve(Set.of("db-password"));

    // then
    assertThat(results).containsEntry("db-password", new Resolved("s3cr3t"));
    assertThat(hashiCorpVault.logins).hasValue(2);
  }

  @Test
  void shouldReturnAccessDeniedWhenPolicyDeniesPathEvenAfterSigningInAgain() {
    // given
    hashiCorpVault.deniedPaths.add("other-team");
    store = createStore(config(null, null));

    // when
    final var result = store.resolve(Set.of("other-team")).get("other-team");

    // then
    assertThat(result)
        .isInstanceOfSatisfying(
            Failed.class,
            failed -> assertThat(failed.code()).isEqualTo(SecretErrorCode.ACCESS_DENIED));
    assertThat(hashiCorpVault.logins).hasValue(2);
  }

  @ParameterizedTest
  @ValueSource(ints = {429, 500, 503})
  void shouldThrowStoreUnavailableForServerSideFailures(final int status) {
    // given
    store = createStore(config(null, null));
    hashiCorpVault.forcedStatus = status;

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("db-password")))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("HTTP " + status);
  }

  @Test
  void shouldThrowStoreUnavailableWhenUnreachable() throws IOException {
    // given
    final int closedPort;
    try (final var socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    store = createStore(config(URI.create("http://127.0.0.1:" + closedPort), null, null));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("db-password")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowStoreUnavailableWhenSecretIdIsRejected() throws IOException {
    // given
    Files.writeString(secretIdFile, "wrong");
    store = createStore(config(null, null));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("db-password")))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("HTTP 400")
        .hasMessageNotContaining("wrong");
    assertThat(
            meterRegistry
                .get(HashiCorpVaultSession.OPERATIONS_METRIC)
                .tags("operation", "login", "result", "failure")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void shouldReadSecretIdAgainOnEverySignIn() throws IOException {
    // given
    hashiCorpVault.entries.put("db-password", "{\"value\":\"s3cr3t\"}");
    store = createStore(config(null, null));
    store.resolve(Set.of("db-password"));

    // when: the secret_id is rotated, and the old token stops working
    hashiCorpVault.acceptedSecretId = "secret-id-2";
    Files.writeString(secretIdFile, "secret-id-2");
    hashiCorpVault.revokeCurrentToken();

    // then
    assertThat(store.resolve(Set.of("db-password")))
        .containsEntry("db-password", new Resolved("s3cr3t"));
  }

  @Test
  void shouldReadSecretIdFromEnvironmentVariable() {
    // given
    hashiCorpVault.entries.put("db-password", "{\"value\":\"s3cr3t\"}");
    final var config =
        new HashiCorpVaultStoreConfig(
            hashiCorpVault.address(),
            null,
            2,
            "secret",
            null,
            null,
            "value",
            AuthMethod.APPROLE,
            "approle",
            FakeHashiCorpVault.ROLE_ID,
            null,
            "MY_SECRET_ID",
            HashiCorpVaultStoreConfig.DEFAULT_KUBERNETES_JWT_PATH,
            null,
            null,
            null,
            true,
            Duration.ofSeconds(2));
    store =
        new HashiCorpVaultSecretStore(
            config,
            "test",
            meterRegistry,
            Tags.empty(),
            name -> name.equals("MY_SECRET_ID") ? hashiCorpVault.acceptedSecretId : null);

    // when / then
    assertThat(store.resolve(Set.of("db-password")))
        .containsEntry("db-password", new Resolved("s3cr3t"));
  }

  @Test
  void shouldRenewTokenBeforeItExpires() {
    // given
    hashiCorpVault.loginLeaseSeconds = 3;
    hashiCorpVault.renewLeaseSeconds = 3;
    store = createStore(config(null, null));

    // when
    store.validateConnectivity();

    // then: renewed at 2/3 of the TTL, without signing in again
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(hashiCorpVault.renewals.get()).isGreaterThanOrEqualTo(2));
    assertThat(hashiCorpVault.logins).hasValue(1);
  }

  @Test
  void shouldSignInAgainWhenTokenApproachesMaxTtl() {
    // given: each renewal grants less than half of the original TTL, as near max_ttl
    hashiCorpVault.loginLeaseSeconds = 3;
    hashiCorpVault.renewLeaseSeconds = 1;
    store = createStore(config(null, null));

    // when
    store.validateConnectivity();

    // then
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(hashiCorpVault.logins.get()).isGreaterThanOrEqualTo(2));
  }

  @Test
  void shouldSignInAgainWhenTokenIsNotRenewable() {
    // given
    hashiCorpVault.loginLeaseSeconds = 2;
    hashiCorpVault.renewable = false;
    store = createStore(config(null, null));

    // when
    store.validateConnectivity();

    // then
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(hashiCorpVault.logins.get()).isGreaterThanOrEqualTo(2));
    assertThat(hashiCorpVault.renewals).hasValue(0);
  }

  @Test
  void shouldRevokeTokenAndStopRenewingOnClose() throws InterruptedException {
    // given
    hashiCorpVault.loginLeaseSeconds = 3;
    store = createStore(config(null, null));
    store.validateConnectivity();

    // when
    store.close();
    store = null;

    // then
    assertThat(hashiCorpVault.revocations).hasValue(1);
    await()
        .during(Duration.ofSeconds(3))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(() -> assertThat(hashiCorpVault.renewals).hasValue(0));
  }

  @Test
  void shouldResolveAllNamesFromContainerEntryWithOneRead() {
    // given
    hashiCorpVault.entries.put(
        "camunda/connectors", "{\"api-key\":\"k\",\"db-password\":\"p\",\"port\":1}");
    store = createStore(config("camunda/", "connectors"));

    // when
    final var results = store.resolve(Set.of("api-key", "db-password", "missing", "port"));

    // then
    assertThat(results)
        .containsEntry("api-key", new Resolved("k"))
        .containsEntry("db-password", new Resolved("p"));
    assertThat(((Failed) results.get("missing")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
    assertThat(((Failed) results.get("port")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(hashiCorpVault.reads).hasValue(1);
    assertThat(store.namesPerCall()).isEqualTo(Integer.MAX_VALUE);
    assertThat(store.list()).containsExactlyInAnyOrder("api-key", "db-password", "port");
  }

  private HashiCorpVaultSecretStore createStore(final HashiCorpVaultStoreConfig config) {
    return new HashiCorpVaultSecretStore(config, "test", meterRegistry, Tags.empty(), name -> null);
  }

  private HashiCorpVaultStoreConfig config(
      final @Nullable String pathPrefix, final @Nullable String containerPath) {
    return config(hashiCorpVault.address(), pathPrefix, containerPath);
  }

  private HashiCorpVaultStoreConfig config(
      final URI address, final @Nullable String pathPrefix, final @Nullable String containerPath) {
    return new HashiCorpVaultStoreConfig(
        address,
        null,
        2,
        "secret",
        pathPrefix,
        containerPath,
        "value",
        AuthMethod.APPROLE,
        "approle",
        FakeHashiCorpVault.ROLE_ID,
        secretIdFile.toString(),
        null,
        HashiCorpVaultStoreConfig.DEFAULT_KUBERNETES_JWT_PATH,
        null,
        null,
        null,
        true,
        Duration.ofSeconds(2));
  }
}
