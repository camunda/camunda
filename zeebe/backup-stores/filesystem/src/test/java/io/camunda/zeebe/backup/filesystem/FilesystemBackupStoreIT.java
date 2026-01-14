/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.testkit.BackupStoreTestKit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class FilesystemBackupStoreIT implements BackupStoreTestKit {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);

  public FilesystemBackupConfig backupConfig;
  public FilesystemBackupStore backupStore;

  @TempDir Path backupDir;

  @BeforeEach
  public void setUpStore() {
    backupConfig = new FilesystemBackupConfig.Builder().withBasePath(backupDir.toString()).build();
    backupStore =
        new FilesystemBackupStore(backupConfig, Executors.newVirtualThreadPerTaskExecutor());
  }

  @Override
  public FilesystemBackupStore getStore() {
    return backupStore;
  }

  @Override
  public Class<? extends Exception> getBackupInInvalidStateExceptionClass() {
    return UnexpectedManifestState.class;
  }

  @Override
  public Class<? extends Exception> getFileNotFoundExceptionClass() {
    return NoSuchFileException.class;
  }

  @ParameterizedTest
  @MethodSource("provideBackups")
  void backupShouldExistAfterStoreIsClosed(final Backup backup) {
    // given
    getStore().save(backup).join();
    final var firstStatus = getStore().getStatus(backup.id()).join();

    // when
    getStore().closeAsync().join();
    setUpStore();

    // then
    final var status = getStore().getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
    assertThat(status.lastModified()).isEqualTo(firstStatus.lastModified());
  }

  @ParameterizedTest
  @MethodSource("provideBackups")
  void cannotDeleteUploadingBlock(final Backup backup) {

    // given when
    uploadInProgressManifest(backup);

    // then
    assertThat(getStore().delete(backup.id()))
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(UnexpectedManifestState.class)
        .withMessageContaining(
            """
                Cannot delete Backup with id \
                'BackupId{node=1, partition=2, checkpoint=3}' \
                while saving is in progress.""");
  }

  @ParameterizedTest
  @MethodSource("provideBackups")
  void cannotRestoreUploadingBackup(final Backup backup, @TempDir final Path targetDir) {
    // when
    uploadInProgressManifest(backup);

    // then
    assertThat(getStore().restore(backup.id(), targetDir))
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(UnexpectedManifestState.class)
        .withMessageContaining(
            """
                Expected to restore from completed backup with id \
                'BackupId{node=1, partition=2, checkpoint=3}', \
                but was in state 'IN_PROGRESS'""");
  }

  void uploadInProgressManifest(final Backup backup) {
    final var manifest = Manifest.createInProgress(backup);
    final byte[] serializedManifest;

    final ManifestManager manifestManager = new ManifestManager(backupDir.resolve("manifests"));
    try {
      final var path = manifestManager.manifestPath(manifest);
      Files.createDirectories(path.getParent());

      serializedManifest = MAPPER.writeValueAsBytes(manifest);
      Files.write(path, serializedManifest, StandardOpenOption.CREATE_NEW);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
