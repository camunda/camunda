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
import io.camunda.zeebe.backup.azure.manifest.Manifest.StatusCode;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.testkit.DeletingBackup;
import io.camunda.zeebe.backup.testkit.QueryingBackupStatus;
import io.camunda.zeebe.backup.testkit.SavingBackup;
import io.camunda.zeebe.backup.testkit.UpdatingBackupStatus;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreIT
    implements SavingBackup, QueryingBackupStatus, UpdatingBackupStatus, DeletingBackup {

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
  public void cannotDeleteUploadingBlock() throws IOException, InterruptedException {
    // given
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));

    Files.write(seg1, RandomUtils.nextBytes(50 * 1024 * 1024));

    final Backup largeBackup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 4, 5, "test"),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of("segment-file-1", seg1)));

    // when
    // Takes long to save 50MB
    getStore().save(largeBackup);
    // Await just enough to the manifest to be written
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                getStore()
                    .getStatus(largeBackup.id())
                    .join()
                    .statusCode()
                    .toString()
                    .equals(StatusCode.IN_PROGRESS.toString()));

    // then
    Assertions.assertThat(getStore().delete(largeBackup.id()))
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(UnexpectedManifestState.class);
  }
}
