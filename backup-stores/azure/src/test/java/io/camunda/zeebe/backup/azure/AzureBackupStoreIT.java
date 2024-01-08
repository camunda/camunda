/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.testkit.QueryingBackupStatus;
import io.camunda.zeebe.backup.testkit.SavingBackup;
import io.camunda.zeebe.backup.testkit.UpdatingBackupStatus;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.FileNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreIT
    implements SavingBackup, QueryingBackupStatus, UpdatingBackupStatus {

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  public AzureBackupConfig azureBackupConfig;
  public AzureBackupStore azureBackupStore;
  public final String containerName = UUID.randomUUID().toString();

  @BeforeEach
  public void setUpBlobClient() {
    azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectString())
            .withContainerName(containerName)
            .build();
    azureBackupStore = new AzureBackupStore(azureBackupConfig);
  }

  @Override
  public AzureBackupStore getStore() {
    return azureBackupStore;
  }

  @Override
  public Class<? extends Exception> getBackupInInvalidStateExceptionClass() {
    return UnexpectedManifestState.class;
  }

  @Override
  public Class<? extends Exception> getFileNotFoundExceptionClass() {
    return FileNotFoundException.class;
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void backupShouldExistAfterStoreIsClosed(final Backup backup) {
    // given
    getStore().save(backup).join();
    final var firstStatus = getStore().getStatus(backup.id()).join();

    // when
    getStore().closeAsync().join();
    setUpBlobClient();

    // then
    final var status = getStore().getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
    assertThat(status.lastModified()).isEqualTo(firstStatus.lastModified());
  }

  @Test
  void cannotDeleteUploadingBlock() {
    // TODO: when delete feature is done
  }
}
