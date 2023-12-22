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
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.azure.manifest.Manifest.StatusCode;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.testkit.SavingBackup;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.RandomUtils;
import org.awaitility.Awaitility;
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
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    Files.write(seg1, RandomUtils.nextBytes(1));

    final Backup backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 4, 5, "test"),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of("segment-file-1", seg1)));

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
  void azureStoreSupportsMultipleConnections() throws ExecutionException, InterruptedException {
    // given
    final BackupIdentifierImpl backupId1 = new BackupIdentifierImpl(3, 2, 1);
    final BackupIdentifierImpl backupId2 = new BackupIdentifierImpl(6, 5, 4);
    final ExecutorService executorService = Executors.newCachedThreadPool();

    // when
    final Future<?> firstCallFuture =
        executorService.submit(() -> executeLargeBackupSave(backupId1));
    final Future<?> secondCallFuture =
        executorService.submit(() -> executeLargeBackupSave(backupId2));

    Awaitility.await()
        .atLeast(Duration.ofMillis(100))
        .atMost(Duration.ofSeconds(5))
        .with()
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () ->
                assertThat(getStore().getStatus(backupId1).join().statusCode().toString())
                    .matches(StatusCode.IN_PROGRESS.toString()));

    assertThat(getStore().getStatus(backupId1).join().statusCode().toString())
        .isEqualTo(StatusCode.IN_PROGRESS.toString());
    assertThat(getStore().getStatus(backupId2).join().statusCode().toString())
        .isEqualTo(StatusCode.IN_PROGRESS.toString());

    firstCallFuture.get();
    secondCallFuture.get();

    assertThat(getStore().getStatus(backupId1).join().statusCode().toString())
        .isEqualTo(StatusCode.COMPLETED.toString());
    assertThat(getStore().getStatus(backupId2).join().statusCode().toString())
        .isEqualTo(StatusCode.COMPLETED.toString());
    executorService.shutdown();
  }

  @Test
  void cannotDeleteUploadingBlock() {
    // TODO: when delete feature is done
  }

  private void executeLargeBackupSave(final BackupIdentifier backupIdentifier) {
    try {
      final var tempDir = Files.createTempDirectory("backup");
      Files.createDirectory(tempDir.resolve("segments/"));
      final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
      Files.write(seg1, RandomUtils.nextBytes(1));

      final Backup backup =
          new BackupImpl(
              backupIdentifier,
              new BackupDescriptorImpl(Optional.empty(), 4, 5, "test"),
              new NamedFileSetImpl(Map.of()),
              new NamedFileSetImpl(Map.of("segment-file-1", seg1)));
      Files.write(seg1, RandomUtils.nextBytes(1024 * 1024 * 20)); // 20 MB

      getStore().save(backup).join();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
