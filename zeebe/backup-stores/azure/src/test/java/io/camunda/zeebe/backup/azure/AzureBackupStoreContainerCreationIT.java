/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import com.azure.storage.blob.BlobServiceClientBuilder;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreContainerCreationIT {

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  public AzureBackupConfig azureBackupConfig;
  public final String containerName = UUID.randomUUID().toString();

  @Test
  void shouldFailToInitializeStore() {
    // given
    azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectString())
            .withContainerName(containerName)
            .withCreateContainer(false)
            .build();

    // then we should fail to create the store since the container does not
    // exist yet.
    assertThrowsExactly(
        AzureBackupStoreException.ContainerDoesNotExist.class,
        () -> new AzureBackupStore(azureBackupConfig));

    // when we create the container
    new BlobServiceClientBuilder()
        .connectionString(AZURITE_CONTAINER.getConnectString())
        .buildClient()
        .getBlobContainerClient(containerName)
        .create();

    // then we can create the store without any exceptions
    assertDoesNotThrow(() -> new AzureBackupStore(azureBackupConfig));
  }
}
