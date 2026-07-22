/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

@Testcontainers
class AwsSecretsManagerSecretStoreIT {

  @Container
  private static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.SECRETSMANAGER);

  private static SecretsManagerClient adminClient;

  @BeforeAll
  static void setUp() {
    adminClient =
        SecretsManagerClient.builder()
            .endpointOverride(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER))
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .build();

    createSecret("camunda/db-password", "s3cr3t");
    createSecret("camunda/api-token", "tok3n");
    createSecret("other/unrelated", "ignored");
    createSecret("camunda/app-config", "{\"DB_PASSWORD\":\"c0nt41ner\",\"API_KEY\":\"k3y\"}");
  }

  @AfterAll
  static void tearDown() {
    if (adminClient != null) {
      adminClient.close();
    }
  }

  @Test
  void shouldResolveSecretFromLocalStack() {
    // given
    try (final var store = storeWithClient("camunda/")) {
      // when
      final var ref = "db-password";
      final var result = store.resolve(Set.of(ref));

      // then
      assertThat(result.get(ref))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("s3cr3t");
    }
  }

  @Test
  void shouldReturnNotFoundForMissingSecret() {
    // given
    try (final var store = storeWithClient("camunda/")) {
      // when
      final var ref = "does-not-exist";
      final var result = store.resolve(Set.of(ref));

      // then — the per-item error entry from BatchGetSecretValue maps to NOT_FOUND
      assertThat(result.get(ref))
          .isInstanceOf(Failed.class)
          .extracting(r -> ((Failed) r).code())
          .isEqualTo(SecretErrorCode.NOT_FOUND);
    }
  }

  @Test
  void shouldResolveMultipleSecretsWithBatchingDisabled() {
    // given — the default path: one GetSecretValue call per reference
    try (final var store = storeWithClient("camunda/")) {
      // when
      final var dbPassword = "db-password";
      final var apiToken = "api-token";
      final var missing = "does-not-exist";
      final var result = store.resolve(Set.of(dbPassword, apiToken, missing));

      // then
      assertThat(result.get(dbPassword))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("s3cr3t");
      assertThat(result.get(apiToken))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("tok3n");
      assertThat(result.get(missing)).isInstanceOf(Failed.class);
    }
  }

  @Test
  void shouldResolveMultipleSecretsInOneBatchCallWhenBatchingEnabled() {
    // given — opt-in batching: one BatchGetSecretValue call resolves all three at once
    try (final var store = storeWithBatchClient("camunda/")) {
      // when
      final var dbPassword = "db-password";
      final var apiToken = "api-token";
      final var missing = "does-not-exist";
      final var result = store.resolve(Set.of(dbPassword, apiToken, missing));

      // then — the found secrets resolve and the missing one is a per-item error, not a
      // store-wide failure
      assertThat(result.get(dbPassword))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("s3cr3t");
      assertThat(result.get(apiToken))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("tok3n");
      assertThat(result.get(missing)).isInstanceOf(Failed.class);
    }
  }

  @Test
  void shouldListSecretsUnderPrefix() {
    // given
    try (final var store = storeWithClient("camunda/")) {
      // when
      final var refs = store.list();

      // then — only camunda/* secrets, prefix stripped
      assertThat(refs).contains("db-password", "api-token").doesNotContain("unrelated");
    }
  }

  @Test
  void shouldResolveViaFromConfigIdentityPath() {
    // given — exercise the production build path (DefaultCredentialsProvider) with
    // credentials supplied via system properties, mirroring how a pod identity injects them
    System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
    System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    try {
      final var config =
          new AwsSecretsManagerStoreConfig(
              LOCALSTACK.getRegion(),
              "camunda/",
              null,
              URI.create(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER).toString()),
              AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
              false,
              AwsSecretsManagerStoreConfig.DEFAULT_BATCH_SIZE);

      try (final var store = AwsSecretsManagerSecretStore.fromConfig(config)) {
        // when
        final var ref = "api-token";
        final var result = store.resolve(Set.of(ref));

        // then
        assertThat(result.get(ref))
            .isInstanceOf(Resolved.class)
            .extracting(r -> ((Resolved) r).value())
            .isEqualTo("tok3n");
      }
    } finally {
      System.clearProperty("aws.accessKeyId");
      System.clearProperty("aws.secretAccessKey");
    }
  }

  @Test
  void shouldResolveViaFromConfigWithBatchingEnabled() {
    // given — the opt-in flag wired all the way through fromConfig() to a real
    // BatchGetSecretValue call against LocalStack, not just via the raw constructor
    System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
    System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    try {
      final var config =
          new AwsSecretsManagerStoreConfig(
              LOCALSTACK.getRegion(),
              "camunda/",
              null,
              URI.create(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER).toString()),
              AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
              true,
              AwsSecretsManagerStoreConfig.DEFAULT_BATCH_SIZE);

      try (final var store = AwsSecretsManagerSecretStore.fromConfig(config)) {
        // when
        final var ref = "api-token";
        final var result = store.resolve(Set.of(ref));

        // then
        assertThat(result.get(ref))
            .isInstanceOf(Resolved.class)
            .extracting(r -> ((Resolved) r).value())
            .isEqualTo("tok3n");
      }
    } finally {
      System.clearProperty("aws.accessKeyId");
      System.clearProperty("aws.secretAccessKey");
    }
  }

  private static AwsSecretsManagerSecretStore storeWithClient(final String prefix) {
    return new AwsSecretsManagerSecretStore(localStackClient(), prefix);
  }

  private static AwsSecretsManagerSecretStore storeWithBatchClient(final String prefix) {
    return new AwsSecretsManagerSecretStore(
        localStackClient(), prefix, true, AwsSecretsManagerStoreConfig.DEFAULT_BATCH_SIZE);
  }

  private static SecretsManagerClient localStackClient() {
    return SecretsManagerClient.builder()
        .endpointOverride(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER))
        .region(Region.of(LOCALSTACK.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
        .build();
  }

  private static void createSecret(final String name, final String value) {
    adminClient.createSecret(CreateSecretRequest.builder().name(name).secretString(value).build());
  }
}
