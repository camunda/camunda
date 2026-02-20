/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.RangeEntry;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RestoreManagerTest {

  @Test
  void shouldFailWhenDirectoryIsNotEmpty(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    try (final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry())) {

      // when
      Files.createDirectory(dir.resolve("other-data"));

      // then
      assertThatThrownBy(() -> restoreManager.restore(1L, false, List.of()))
          .isInstanceOf(DirectoryNotEmptyException.class);
    }
  }

  @Test
  void shouldIgnoreConfigurableFilesInTarget(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    try (final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry())) {

      // when - create ignored files
      Files.createDirectory(dir.resolve("lost+found"));
      Files.createFile(dir.resolve(".DS_Store"));
      Files.createFile(dir.resolve("Thumbs.db"));

      // then - should not fail because all files are ignored
      assertThatThrownBy(
              () ->
                  restoreManager.restore(
                      1L, false, List.of("lost+found", ".DS_Store", "Thumbs.db")))
          .hasRootCauseInstanceOf(BackupNotFoundException.class);
    }
  }

  @Test
  void shouldFailWhenNonIgnoredFileExists(@TempDir final Path dir) throws IOException {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    try (final var restoreManager =
        new RestoreManager(
            configuration, new TestRestorableBackupStore(), new SimpleMeterRegistry())) {

      // when - create ignored and non-ignored files
      Files.createDirectory(dir.resolve("lost+found"));
      Files.createFile(dir.resolve("some-data-file"));

      // then - should fail because some-data-file is not ignored
      assertThatThrownBy(() -> restoreManager.restore(1L, false, List.of("lost+found")))
          .isInstanceOf(DirectoryNotEmptyException.class);
    }
  }

  @Test
  void shouldFailWhenBackupsAreNotContinuous(@TempDir final Path dir) {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(2);

    final var backupStore = new TestRestorableBackupStore();

    // Partition 1: single continuous range [1, 5]
    backupStore.storeManifest(
        new BackupMetadataManifest(
            1, 1L, Instant.now(), List.of(), List.of(new RangeEntry(1L, 5L))));

    // Partition 2: two ranges with a gap [1,3] and [5,7] — no single range covers [1,5]
    backupStore.storeManifest(
        new BackupMetadataManifest(
            2,
            1L,
            Instant.now(),
            List.of(),
            List.of(new RangeEntry(1L, 3L), new RangeEntry(5L, 7L))));

    try (final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry())) {

      // when - trying to restore backups from 1 to 5
      // then - should fail because partition 2 doesn't have a continuous range from 1 to 5
      assertThatThrownBy(
              () -> restoreManager.restore(new long[] {1L, 2L, 3L, 4L, 5L}, false, List.of()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Invalid backup ranges");
    }
  }

  @Test
  void shouldFailWhenBackupsAreNotContinuousUsingTimeRange(@TempDir final Path dir) {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(2);

    final var backupStore = new TestRestorableBackupStore();
    final var generator = new CheckpointIdGenerator();

    // Create 5 checkpoints with timestamps at 1-minute intervals
    final var from = Instant.parse("2024-01-01T10:00:00Z");
    final var checkpointIds = new long[5];
    final var checkpointEntries = new java.util.ArrayList<CheckpointEntry>();
    for (var i = 0; i < 5; i++) {
      final var timestamp = from.plusSeconds((i + 1) * 60);
      final var checkpointId = generator.fromTimestamp(timestamp.toEpochMilli());
      checkpointIds[i] = checkpointId;
      checkpointEntries.add(
          new CheckpointEntry(
              checkpointId, 0L, timestamp, "SCHEDULED_BACKUP", 0L, 2, "test-version"));
      // Add backups to the store for both partitions (needed by restore)
      backupStore.addBackupWithTimestamp(
          new BackupIdentifierImpl(0, 1, checkpointId), timestamp, 2);
      backupStore.addBackupWithTimestamp(
          new BackupIdentifierImpl(0, 2, checkpointId), timestamp, 2);
    }

    // Partition 1: single continuous range covering all checkpoints
    backupStore.storeManifest(
        new BackupMetadataManifest(
            1,
            1L,
            Instant.now(),
            checkpointEntries,
            List.of(new RangeEntry(checkpointIds[0], checkpointIds[4]))));

    // Partition 2: two ranges with a gap (0-1, then 3-4)
    backupStore.storeManifest(
        new BackupMetadataManifest(
            2,
            1L,
            Instant.now(),
            checkpointEntries,
            List.of(
                new RangeEntry(checkpointIds[0], checkpointIds[1]),
                new RangeEntry(checkpointIds[3], checkpointIds[4]))));

    try (final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry())) {

      // when - trying to restore backups from 'from' to 'from' + 5 minutes
      // then - should fail because partition 2 doesn't have a continuous range
      final var to = from.plusSeconds(5 * 60);
      assertThatThrownBy(() -> restoreManager.restore(from, to, false, List.of()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Invalid backup ranges");
    }
  }
}
