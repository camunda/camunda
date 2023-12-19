/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigIT {

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
    // given a valid connection string
    final String connectionString =
        "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
            + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            + "BlobEndpoint=http://127.0.0.1:";

    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(connectionString)
            .withContainerName(UUID.randomUUID().toString())
            .build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailValidationBecauseOfMissingAccountName() {
    final AzureBackupConfig azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withEndpoint("http://127.0.0.1:10000/devstoreaccount1")
            .withAccountName(null)
            .withAccountKey(
                "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==")
            .withContainerName(UUID.randomUUID().toString())
            .build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig))
        .hasMessage("'accountName' cannot be null.");
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureBackupConfig))
        .hasMessage(
            "Connection string, or all of connection information (account name, account key, and endpoint) must be provided.");
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
        .hasMessage(
            "Connection string, or all of connection information (account name, account key, and endpoint) must be provided.");
  }
}
