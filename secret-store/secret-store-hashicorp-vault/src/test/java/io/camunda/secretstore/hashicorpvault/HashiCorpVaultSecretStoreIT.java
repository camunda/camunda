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

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.secretstore.hashicorpvault.HashiCorpVaultStoreConfig.AuthMethod;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

/**
 * Runs the store against a real HashiCorp Vault server (dev mode), signing in with AppRole the way
 * a production deployment would: the role id in configuration, the secret id in a file.
 */
@Testcontainers
class HashiCorpVaultSecretStoreIT {

  private static final String ROOT_TOKEN = "root-token";

  @Container
  private static final VaultContainer<?> HASHICORP_VAULT =
      new VaultContainer<>("hashicorp/vault:2.2.0").withVaultToken(ROOT_TOKEN);

  private static final String POLICY =
      """
      path "secret/data/camunda/*" { capabilities = ["read"] }
      path "secret/metadata/camunda/*" { capabilities = ["list"] }
      path "secret/metadata/camunda" { capabilities = ["list"] }
      path "kv1/camunda/*" { capabilities = ["read", "list"] }
      """;

  @TempDir private static Path tempDir;

  private static String roleId;
  private static Path secretIdFile;
  private static String shortRoleId;
  private static Path shortSecretIdFile;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final List<HashiCorpVaultSecretStore> stores = new ArrayList<>();

  @BeforeAll
  static void setUpHashiCorpVault() throws Exception {
    hashiCorpVault("kv", "put", "secret/camunda/db-password", "value=s3cr3t");
    hashiCorpVault("kv", "put", "secret/camunda/api-token", "value=tok3n");
    hashiCorpVault("kv", "put", "secret/camunda/multi-field", "value=v", "other=o");
    hashiCorpVault("kv", "put", "secret/other/unrelated", "value=forbidden");
    hashiCorpVault(
        "kv", "put", "secret/camunda/app-config", "DB_PASSWORD=c0nt41ner", "API_KEY=k3y");
    hashiCorpVault("secrets", "enable", "-path=kv1", "-version=1", "kv");
    hashiCorpVault("kv", "put", "kv1/camunda/db-password", "value=v1-s3cr3t");

    shell("printf '%s' '" + POLICY + "' > /tmp/camunda.hcl");
    hashiCorpVault("policy", "write", "camunda", "/tmp/camunda.hcl");
    hashiCorpVault("auth", "enable", "approle");
    hashiCorpVault(
        "write",
        "auth/approle/role/camunda",
        "token_policies=camunda",
        "token_ttl=1h",
        "token_max_ttl=4h");
    hashiCorpVault(
        "write",
        "auth/approle/role/camunda-short",
        "token_policies=camunda",
        "token_ttl=5s",
        "token_max_ttl=15s");

    roleId = hashiCorpVault("read", "-field=role_id", "auth/approle/role/camunda/role-id");
    secretIdFile =
        writeSecretId(
            "secret-id",
            hashiCorpVault(
                "write", "-f", "-field=secret_id", "auth/approle/role/camunda/secret-id"));
    shortRoleId =
        hashiCorpVault("read", "-field=role_id", "auth/approle/role/camunda-short/role-id");
    shortSecretIdFile =
        writeSecretId(
            "short-secret-id",
            hashiCorpVault(
                "write", "-f", "-field=secret_id", "auth/approle/role/camunda-short/secret-id"));
  }

  @AfterEach
  void closeStores() {
    stores.forEach(HashiCorpVaultSecretStore::close);
  }

  @Test
  void shouldResolveKvV2EntriesWithAppRole() {
    // given
    final var store = store(config(2, "camunda/", null, roleId, secretIdFile));

    // when
    final var result = store.resolve(Set.of("db-password", "api-token", "does-not-exist"));

    // then
    assertThat(result)
        .containsEntry("db-password", new Resolved("s3cr3t"))
        .containsEntry("api-token", new Resolved("tok3n"));
    assertThat(result.get("does-not-exist"))
        .extracting(this::code)
        .isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldResolveKvV1Entries() {
    // given
    final var store = store(config(1, "camunda/", null, roleId, secretIdFile, "kv1"));

    // when / then
    assertThat(store.resolve(Set.of("db-password")))
        .containsEntry("db-password", new Resolved("v1-s3cr3t"));
  }

  @Test
  void shouldResolveFieldsOfAContainerEntryInOneRead() {
    // given
    final var store = store(config(2, "camunda/", "app-config", roleId, secretIdFile));

    // when
    final var result = store.resolve(Set.of("DB_PASSWORD", "API_KEY", "MISSING"));

    // then
    assertThat(result)
        .containsEntry("DB_PASSWORD", new Resolved("c0nt41ner"))
        .containsEntry("API_KEY", new Resolved("k3y"));
    assertThat(result.get("MISSING")).extracting(this::code).isEqualTo(SecretErrorCode.NOT_FOUND);
    assertThat(store.namesPerCall()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldListEntriesAndContainerFields() {
    // given
    final var perEntry = store(config(2, "camunda/", null, roleId, secretIdFile));
    final var container = store(config(2, "camunda/", "app-config", roleId, secretIdFile));

    // when / then
    assertThat(perEntry.list())
        .contains("db-password", "api-token", "app-config")
        .doesNotContain("unrelated");
    assertThat(container.list()).containsExactlyInAnyOrder("DB_PASSWORD", "API_KEY");
  }

  @Test
  void shouldReportAccessDeniedForPathOutsideThePolicy() {
    // given
    final var store = store(config(2, "other/", null, roleId, secretIdFile));

    // when / then
    assertThat(store.resolve(Set.of("unrelated")).get("unrelated"))
        .extracting(this::code)
        .isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldRefusePathTraversalWithoutCallingHashiCorpVault() {
    // given
    final var store = store(config(2, "camunda/", null, roleId, secretIdFile));

    // when
    final var result = store.resolve(Set.of("../other/unrelated", "..", "a/b"));

    // then
    assertThat(result.values()).extracting(this::code).containsOnly(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldKeepResolvingAcrossTokenRenewalsAndMaxTtl() {
    // given: token TTL 5s, max TTL 15s, so the session must renew and then sign in again
    final var store = store(config(2, "camunda/", null, shortRoleId, shortSecretIdFile));
    final var deadline = Instant.now().plus(Duration.ofSeconds(40));
    final List<SecretResolutionResult> failures = new ArrayList<>();
    var resolves = 0;

    // when
    while (Instant.now().isBefore(deadline)) {
      final var result = store.resolve(Set.of("db-password")).get("db-password");
      if (!new Resolved("s3cr3t").equals(result)) {
        failures.add(result);
      }
      resolves++;
      Awaitility.await().pollDelay(Duration.ofMillis(500)).until(() -> true);
    }

    // then
    assertThat(failures).isEmpty();
    assertThat(resolves).isGreaterThan(40);
    assertThat(sessionCount("renew", "success")).isPositive();
    assertThat(sessionCount("login", "success")).isGreaterThanOrEqualTo(3);
  }

  @Test
  void shouldSignInAgainWhenTheTokenIsRevoked() throws Exception {
    // given
    final var store = store(config(2, "camunda/", null, roleId, secretIdFile));
    assertThat(store.resolve(Set.of("db-password")))
        .containsEntry("db-password", new Resolved("s3cr3t"));

    // when
    hashiCorpVault("token", "revoke", "-mode=path", "auth/approle/login");

    // then
    assertThat(store.resolve(Set.of("db-password")))
        .containsEntry("db-password", new Resolved("s3cr3t"));
    assertThat(sessionCount("login", "success")).isEqualTo(2);
  }

  @Test
  void shouldFailAsUnavailableWithWrongSecretId() throws IOException {
    // given
    final var wrong = writeSecretId("wrong-secret-id", "00000000-0000-0000-0000-000000000000");
    final var store = store(config(2, "camunda/", null, roleId, wrong));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("db-password")))
        .isInstanceOf(SecretStoreUnavailableException.class);
    assertThat(sessionCount("login", "failure")).isPositive();
  }

  @Test
  void shouldPickUpARotatedSecretIdFromTheFile() throws Exception {
    // given: a store whose file holds an outdated secret id
    final var file = writeSecretId("rotated-secret-id", "00000000-0000-0000-0000-000000000000");
    final var store = store(config(2, "camunda/", null, roleId, file));
    assertThatThrownBy(() -> store.resolve(Set.of("db-password")))
        .isInstanceOf(SecretStoreUnavailableException.class);

    // when: the secret id is rotated in place
    Files.writeString(file, Files.readString(secretIdFile));

    // then
    assertThat(store.resolve(Set.of("db-password")))
        .containsEntry("db-password", new Resolved("s3cr3t"));
  }

  @Test
  void shouldFailAsUnavailableWhileSealedAndRecoverAfterUnseal() throws Exception {
    // given
    final var store = store(config(2, "camunda/", null, roleId, secretIdFile));
    assertThat(store.resolve(Set.of("db-password")))
        .containsEntry("db-password", new Resolved("s3cr3t"));
    final var unsealKey = unsealKey();

    try {
      // when
      hashiCorpVault("operator", "seal");

      // then
      assertThatThrownBy(() -> store.resolve(Set.of("db-password")))
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageContaining("503");
    } finally {
      hashiCorpVault("operator", "unseal", unsealKey);
    }
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(store.resolve(Set.of("db-password")))
                    .containsEntry("db-password", new Resolved("s3cr3t")));
  }

  private HashiCorpVaultSecretStore store(final HashiCorpVaultStoreConfig config) {
    final var store =
        new HashiCorpVaultSecretStore(config, "it", meterRegistry, Tags.empty(), name -> null);
    stores.add(store);
    return store;
  }

  private double sessionCount(final String operation, final String result) {
    final var counter =
        meterRegistry
            .find("camunda.secret.store.hashicorp.vault.session")
            .tags("operation", operation, "result", result)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private SecretErrorCode code(final SecretResolutionResult result) {
    assertThat(result).isInstanceOf(Failed.class);
    return ((Failed) result).code();
  }

  private static HashiCorpVaultStoreConfig config(
      final int kvVersion,
      final @Nullable String pathPrefix,
      final @Nullable String containerPath,
      final String role,
      final Path secretId) {
    return config(kvVersion, pathPrefix, containerPath, role, secretId, "secret");
  }

  private static HashiCorpVaultStoreConfig config(
      final int kvVersion,
      final @Nullable String pathPrefix,
      final @Nullable String containerPath,
      final String role,
      final Path secretId,
      final String mount) {
    return new HashiCorpVaultStoreConfig(
        URI.create(HASHICORP_VAULT.getHttpHostAddress()),
        null,
        kvVersion,
        mount,
        pathPrefix,
        containerPath,
        HashiCorpVaultStoreConfig.DEFAULT_FIELD,
        AuthMethod.APPROLE,
        AuthMethod.APPROLE.defaultMount(),
        role,
        secretId.toString(),
        null,
        HashiCorpVaultStoreConfig.DEFAULT_KUBERNETES_JWT_PATH,
        null,
        null,
        null,
        true,
        Duration.ofSeconds(5));
  }

  private static Path writeSecretId(final String name, final String secretId) throws IOException {
    return Files.writeString(tempDir.resolve(name), secretId);
  }

  private static String unsealKey() {
    final var matcher = Pattern.compile("Unseal Key: (\\S+)").matcher(HASHICORP_VAULT.getLogs());
    assertThat(matcher.find()).as("dev-mode unseal key in container logs").isTrue();
    return matcher.group(1);
  }

  private static String hashiCorpVault(final String... args) throws Exception {
    final var command = new ArrayList<String>();
    command.add("vault");
    command.addAll(List.of(args));
    final var result = HASHICORP_VAULT.execInContainer(command.toArray(String[]::new));
    assertThat(result.getExitCode()).as("vault %s: %s", args, result.getStderr()).isZero();
    return result.getStdout().trim();
  }

  private static void shell(final String script) throws Exception {
    final var result = HASHICORP_VAULT.execInContainer("sh", "-c", script);
    assertThat(result.getExitCode()).as(result.getStderr()).isZero();
  }
}
