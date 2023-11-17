/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ConfigIT {
  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  public AzureBackupConfig azureBackupConfig;
  public AzureBackupStore azureBackupStore;

  @BeforeEach
  public void setUpClient() {
    azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectStr())
            .withContainerName(UUID.randomUUID().toString())
            .build();
    azureBackupStore = new AzureBackupStore(azureBackupConfig);
  }

  @Test
  void shouldSuccessfullyValidateConfiguration() {
    final AzureBackupConfig azureBackupConfig1 =
        new AzureBackupConfig.Builder()
            .withEndpoint("http://127.0.0.1:10000/devstoreaccount1")
            .withAccountName("devstoreaccount1")
            .withAccountKey(
                "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==")
            .withContainerName(UUID.randomUUID().toString())
            .build();

    final AzureBackupConfig azureBackupConfig2 =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectStr())
            .withContainerName(UUID.randomUUID().toString())
            .build();

    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig1))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> new AzureBackupStore(azureBackupConfig2))
        .doesNotThrowAnyException();
  }
}
