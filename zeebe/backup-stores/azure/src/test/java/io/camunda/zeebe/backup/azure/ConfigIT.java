/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigIT {

  private static final String VALID_ENDPOINT = "https://127.0.0.1";
  private static final String VALID_CONNECTION_STRING =
      "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
          + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
          + "BlobEndpoint=http://127.0.0.1:";

  @Test
  void shouldSuccessfullyValidateCredentialsConfig() {
    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withEndpoint("http://127.0.0.1:10000/devstoreaccount1")
            .withAccountName("devstoreaccount1")
            .withAccountKey(
                "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==")
            .withContainerName(UUID.randomUUID().toString())
            .build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldValidateConnectionStringConfig() {

    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(VALID_CONNECTION_STRING)
            .withContainerName(UUID.randomUUID().toString())
            .build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailDueToMissingEndpointOnAutoAuthConfig() {

    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder().withContainerName(UUID.randomUUID().toString()).build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig))
        .hasMessage("The Azure Storage endpoint url is malformed.");
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .hasMessage("Connection string or endpoint is required");
  }

  @Test
  void shouldValidateAutoAuthConfig() {

    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withEndpoint(VALID_ENDPOINT)
            .withContainerName(UUID.randomUUID().toString())
            .build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailValidationBecauseOfMissingConnectionString() {

    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(null)
            .withContainerName(UUID.randomUUID().toString())
            .build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig))
        .hasMessage("The Azure Storage endpoint url is malformed.");
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .hasMessage("Connection string or endpoint is required");
  }

  @Test
  void shouldFailValidationWithPartialAccountCredentials() {
    Assertions.assertThatCode(
            () ->
                AzureBackupStore.validateConfig(
                    new AzureBackupConfig.Builder()
                        .withEndpoint("test")
                        .withAccountKey("key")
                        .build()))
        .hasMessage("Account key is specified but account name is missing");

    Assertions.assertThatCode(
            () ->
                AzureBackupStore.validateConfig(
                    new AzureBackupConfig.Builder()
                        .withEndpoint("test")
                        .withAccountName("name")
                        .build()))
        .hasMessage("Account name is specified but account key is missing");
  }

  @Test
  void containerNameCannotBeMissing() {
    // given
    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(VALID_CONNECTION_STRING)
            .withContainerName(null)
            .build();

    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .hasMessage("Container name cannot be null.");
  }
}
