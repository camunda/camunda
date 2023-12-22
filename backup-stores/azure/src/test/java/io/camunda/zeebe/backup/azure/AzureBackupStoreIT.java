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
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.testkit.SavingBackup;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreIT implements SavingBackup {

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

  @Test
  void backupShouldExistAfterStoreIsClosed() throws IOException {
    // given
    final Backup backup = backup();

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
  void containerNameCanBeEmpty() throws IOException {
    // given
    final Backup backup = backup();
    final AzureBackupConfig configWithEmptyContainerName =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectString())
            .build();
    final AzureBackupStore store = new AzureBackupStore(configWithEmptyContainerName);

    // when
    store.save(backup).join();

    // then
    final var status = store.getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
  }

  @Test
  void cannotDeleteUploadingBlock() {
    // TODO: when delete feature is done
  }

  private Backup backup() throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-2"));
    Files.write(seg1, RandomUtils.nextBytes(1));

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl(Optional.empty(), 6, 7, "test"),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1)));
  }
}
