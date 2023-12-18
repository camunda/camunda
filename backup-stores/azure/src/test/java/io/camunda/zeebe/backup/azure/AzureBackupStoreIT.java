/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.storage.blob.models.BlobStorageException;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.testkit.SavingBackup;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreIT implements SavingBackup {

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  public AzureBackupConfig azureBackupConfig;
  public AzureBackupStore azureBackupStore;

  @BeforeEach
  public void setUpBlobClient() {
    azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectStr())
            .withContainerName(UUID.randomUUID().toString())
            .build();
    azureBackupStore = new AzureBackupStore(azureBackupConfig);
  }

  @Override
  public AzureBackupStore getStore() {
    return azureBackupStore;
  }

  @Override
  public Class<? extends Exception> getBackupInInvalidStateExceptionClass() {
    return BlobStorageException.class;
  }

  @Override
  public void shouldNotOverwriteCompletedBackup(final Backup backup) {
    // requires get status (which is not yet implemented
  }
}
