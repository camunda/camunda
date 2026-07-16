/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
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
      final var ref = new AwsSecretsManagerSecretReference("db-password");
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
      final var ref = new AwsSecretsManagerSecretReference("does-not-exist");
      final var result = store.resolve(Set.of(ref));

      // then — the per-item error entry from BatchGetSecretValue maps to NOT_FOUND
      assertThat(result.get(ref))
          .isInstanceOf(Failed.class)
          .extracting(r -> ((Failed) r).code())
          .isEqualTo(SecretErrorCode.NOT_FOUND);
    }
  }

  @Test
  void shouldResolveMultipleSecretsInOneBatchCall() {
    // given
    try (final var store = storeWithClient("camunda/")) {
      // when
      final var dbPassword = new AwsSecretsManagerSecretReference("db-password");
      final var apiToken = new AwsSecretsManagerSecretReference("api-token");
      final var missing = new AwsSecretsManagerSecretReference("does-not-exist");
      final var result = store.resolve(Set.of(dbPassword, apiToken, missing));

      // then — one BatchGetSecretValue call resolves the found secrets and reports the missing
      // one as a per-item error, not a store-wide failure
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
      assertThat(refs)
          .contains(
              new AwsSecretsManagerSecretReference("db-password"),
              new AwsSecretsManagerSecretReference("api-token"))
          .doesNotContain(new AwsSecretsManagerSecretReference("unrelated"));
    }
  }

  @Test
  void shouldResolveViaFromConfigIdentityPath() {
    // given — exercise the production build path (DefaultCredentialsProvider) with
    // credentials supplied via system properties, mirroring how a pod identity injects them.
    // fromConfig() succeeding at all already proves its startup connectivity validation passed.
    System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
    System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    try {
      final var config =
          new AwsSecretsManagerStoreConfig(
              LOCALSTACK.getRegion(),
              "camunda/",
              URI.create(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER).toString()),
              AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES);

      try (final var store = AwsSecretsManagerSecretStore.fromConfig(config)) {
        // when
        final var ref = new AwsSecretsManagerSecretReference("api-token");
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
  void shouldFailFastOnUnreachableEndpoint() {
    // given — an endpoint nothing listens on, mimicking a network/DNS misconfiguration
    System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
    System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    try {
      final var config =
          new AwsSecretsManagerStoreConfig(
              LOCALSTACK.getRegion(),
              "camunda/",
              URI.create("http://127.0.0.1:1"),
              // fail fast, don't waste the test retrying an already-deterministic connection
              // refusal
              0);

      // when / then — fromConfig() itself must fail, not the first resolve()/list() call
      assertThatThrownBy(() -> AwsSecretsManagerSecretStore.fromConfig(config))
          .isInstanceOf(SecretStoreUnavailableException.class);
    } finally {
      System.clearProperty("aws.accessKeyId");
      System.clearProperty("aws.secretAccessKey");
    }
  }

  private static AwsSecretsManagerSecretStore storeWithClient(final String prefix) {
    final var client =
        SecretsManagerClient.builder()
            .endpointOverride(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER))
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .build();
    return new AwsSecretsManagerSecretStore(client, prefix);
  }

  private static void createSecret(final String name, final String value) {
    adminClient.createSecret(CreateSecretRequest.builder().name(name).secretString(value).build());
  }
}
