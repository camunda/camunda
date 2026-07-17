/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static org.assertj.core.api.Assertions.assertThat;

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

      // then
      assertThat(result.get(ref)).isInstanceOf(Failed.class);
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
    // credentials supplied via system properties, mirroring how a pod identity injects them
    System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
    System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    try {
      final var config =
          new AwsSecretsManagerStoreConfig(
              LOCALSTACK.getRegion(),
              "camunda/",
              URI.create(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER).toString()),
              AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
              null);

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
  void shouldResolveMultipleKeysFromJsonContainerSecret() {
    // given — one JSON secret holds multiple key-value pairs
    try (final var store = storeWithContainerClient("camunda/", "app-config")) {
      // when
      final var dbPassword = new AwsSecretsManagerSecretReference("DB_PASSWORD");
      final var apiKey = new AwsSecretsManagerSecretReference("API_KEY");
      final var missing = new AwsSecretsManagerSecretReference("MISSING_KEY");
      final var result = store.resolve(Set.of(dbPassword, apiKey, missing));

      // then — only one underlying secret is fetched, but every key resolves independently
      assertThat(result.get(dbPassword))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("c0nt41ner");
      assertThat(result.get(apiKey))
          .isInstanceOf(Resolved.class)
          .extracting(r -> ((Resolved) r).value())
          .isEqualTo("k3y");
      assertThat(result.get(missing)).isInstanceOf(Failed.class);
    }
  }

  @Test
  void shouldListKeysFromJsonContainerSecret() {
    // given
    try (final var store = storeWithContainerClient("camunda/", "app-config")) {
      // when
      final var refs = store.list();

      // then — one reference per JSON key, not per AWS secret
      assertThat(refs)
          .containsExactlyInAnyOrder(
              new AwsSecretsManagerSecretReference("DB_PASSWORD"),
              new AwsSecretsManagerSecretReference("API_KEY"));
    }
  }

  @Test
  void shouldResolveViaFromConfigWithContainerSecretId() {
    // given — the opt-in container mode wired all the way through fromConfig()
    System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
    System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    try {
      final var config =
          new AwsSecretsManagerStoreConfig(
              LOCALSTACK.getRegion(),
              "camunda/",
              URI.create(LOCALSTACK.getEndpointOverride(Service.SECRETSMANAGER).toString()),
              AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
              "app-config");

      try (final var store = AwsSecretsManagerSecretStore.fromConfig(config)) {
        // when
        final var ref = new AwsSecretsManagerSecretReference("API_KEY");
        final var result = store.resolve(Set.of(ref));

        // then
        assertThat(result.get(ref))
            .isInstanceOf(Resolved.class)
            .extracting(r -> ((Resolved) r).value())
            .isEqualTo("k3y");
      }
    } finally {
      System.clearProperty("aws.accessKeyId");
      System.clearProperty("aws.secretAccessKey");
    }
  }

  private static AwsSecretsManagerSecretStore storeWithClient(final String prefix) {
    return new AwsSecretsManagerSecretStore(localStackClient(), prefix);
  }

  private static AwsSecretsManagerSecretStore storeWithContainerClient(
      final String prefix, final String containerSecretId) {
    return new AwsSecretsManagerSecretStore(localStackClient(), prefix, containerSecretId);
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
