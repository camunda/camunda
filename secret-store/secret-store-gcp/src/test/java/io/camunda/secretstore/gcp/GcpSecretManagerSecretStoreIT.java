/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.protobuf.ByteString;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.secretstore.gcp.util.SecretManagerEmulatorContainer;
import io.grpc.ManagedChannelBuilder;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration tests that drive the real gax {@link SecretManagerServiceClient} against a
 * GCP Secret Manager emulator, built through the production {@link
 * GcpSecretManagerSecretStore#fromConfig} path (client init + startup connectivity validation).
 * Fine-grained error-code classification is covered by {@link GcpSecretManagerSecretStoreTest};
 * this suite proves the wire path works against a real server.
 */
@Testcontainers
class GcpSecretManagerSecretStoreIT {

  private static final String PROJECT = "test-project";
  private static final String PREFIX = "camunda-";
  private static final String CONTAINER_PREFIX = "cfg-";

  @Container
  private static final SecretManagerEmulatorContainer EMULATOR =
      new SecretManagerEmulatorContainer();

  private static SecretManagerServiceClient adminClient;

  @BeforeAll
  static void seed() throws Exception {
    adminClient = SecretManagerServiceClient.create(emulatorSettings());

    createSecret("camunda-db-password", "s3cr3t");
    createSecret("camunda-api-token", "tok3n");
    createSecret("other-unrelated", "ignored");
    createSecret(
        "cfg-app-config", "{\"DB_PASSWORD\":\"c0nt41ner\",\"API_KEY\":\"k3y\",\"NUM\":42}");
    createSecret("cfg-bad-json", "not json");
    createSecret("cfg-arr", "[\"a\",\"b\"]");
  }

  @AfterAll
  static void tearDown() {
    if (adminClient != null) {
      adminClient.close();
    }
  }

  @Test
  void shouldResolveSecretViaFromConfig() {
    // given — the production build path against a real server
    try (final var store = oneByOneStore()) {
      // when
      final var result = store.resolve(Set.of("db-password"));

      // then
      assertThat(result.get("db-password"))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("s3cr3t");
    }
  }

  @Test
  void shouldReturnNotFoundForMissingSecret() {
    // given
    try (final var store = oneByOneStore()) {
      // when
      final var result = store.resolve(Set.of("does-not-exist"));

      // then
      assertThat(((Failed) result.get("does-not-exist")).code())
          .isEqualTo(SecretErrorCode.NOT_FOUND);
    }
  }

  @Test
  void shouldResolveBatchWithHitAndMiss() {
    // given
    try (final var store = oneByOneStore()) {
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
  }

  @Test
  void shouldListSecretsUnderPrefixWithPrefixStripped() {
    // given
    try (final var store = oneByOneStore()) {
      // when
      final var refs = store.list();

      // then — only camunda-* secrets, prefix stripped; other-* and cfg-* excluded
      assertThat(refs).containsExactlyInAnyOrder("db-password", "api-token");
    }
  }

  @Test
  void shouldResolveMultipleKeysFromContainer() {
    // given
    try (final var store = containerStore("app-config")) {
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
  }

  @Test
  void shouldReturnNotFoundForMissingKeyInContainer() {
    // given
    try (final var store = containerStore("app-config")) {
      // when
      final var result = store.resolve(Set.of("MISSING"));

      // then
      assertThat(((Failed) result.get("MISSING")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
    }
  }

  @Test
  void shouldReturnInvalidRefForNonStringKeyInContainer() {
    // given
    try (final var store = containerStore("app-config")) {
      // when
      final var result = store.resolve(Set.of("NUM"));

      // then
      assertThat(((Failed) result.get("NUM")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    }
  }

  @Test
  void shouldFailAllKeysWhenContainerIsNotValidJson() {
    // given
    try (final var store = containerStore("bad-json")) {
      // when
      final var result = store.resolve(Set.of("A", "B"));

      // then
      assertThat(((Failed) result.get("A")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
      assertThat(((Failed) result.get("B")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    }
  }

  @Test
  void shouldFailAllKeysWhenContainerIsNotJsonObject() {
    // given — a valid JSON array is not usable as a key/value container
    try (final var store = containerStore("arr")) {
      // when
      final var result = store.resolve(Set.of("A"));

      // then
      assertThat(((Failed) result.get("A")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    }
  }

  @Test
  void shouldListKeysFromContainer() {
    // given
    try (final var store = containerStore("app-config")) {
      // when
      final var refs = store.list();

      // then
      assertThat(refs).containsExactlyInAnyOrder("DB_PASSWORD", "API_KEY", "NUM");
    }
  }

  @Test
  void shouldThrowUnavailableWhenContainerListIsNotJsonObject() {
    // given
    try (final var store = containerStore("arr")) {
      // when / then — the malformed-content message must surface, not a generic "unavailable" one
      assertThatThrownBy(store::list)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageContaining("is not a JSON object");
    }
  }

  @Test
  void shouldNotFailFastButSurfaceErrorOnFirstUseWhenEndpointIsUnreachable() {
    // given — the startup connectivity probe is best-effort (mirrors the AWS store): an unreachable
    // endpoint is logged as a warning at build time rather than failing fast
    final var config = new GcpSecretManagerStoreConfig(PROJECT, PREFIX, "localhost:1", null, true);

    // when — the store is still built despite the dead endpoint
    try (final var store = GcpSecretManagerSecretStore.fromConfig(config)) {
      // then — the connectivity failure surfaces on the first real call instead
      assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
    }
  }

  private static GcpSecretManagerSecretStore oneByOneStore() {
    return GcpSecretManagerSecretStore.fromConfig(
        new GcpSecretManagerStoreConfig(PROJECT, PREFIX, EMULATOR.grpcEndpoint(), null, true));
  }

  private static GcpSecretManagerSecretStore containerStore(final String containerSecretId) {
    return GcpSecretManagerSecretStore.fromConfig(
        new GcpSecretManagerStoreConfig(
            PROJECT, CONTAINER_PREFIX, EMULATOR.grpcEndpoint(), containerSecretId, true));
  }

  private static void createSecret(final String secretId, final String value) {
    adminClient.createSecret(
        ProjectName.of(PROJECT),
        secretId,
        Secret.newBuilder()
            .setReplication(
                Replication.newBuilder()
                    .setAutomatic(Replication.Automatic.newBuilder().build())
                    .build())
            .build());
    adminClient.addSecretVersion(
        SecretName.of(PROJECT, secretId),
        SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(value)).build());
  }

  private static SecretManagerServiceSettings emulatorSettings() throws java.io.IOException {
    return SecretManagerServiceSettings.newBuilder()
        .setCredentialsProvider(NoCredentialsProvider.create())
        .setTransportChannelProvider(
            SecretManagerServiceSettings.defaultGrpcTransportProviderBuilder()
                .setEndpoint(EMULATOR.grpcEndpoint())
                .setChannelConfigurator(ManagedChannelBuilder::usePlaintext)
                .build())
        .build();
  }
}
