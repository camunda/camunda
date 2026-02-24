/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState.BackupRange;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState.CheckpointEntry;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
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
  void shouldFailWhenNoCommonCheckpointExistsUsingTimeRange(@TempDir final Path dir) {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(2);

    final var backupStore = new TestRestorableBackupStore();
    final var from = Instant.parse("2024-01-01T10:00:00Z");
    final var to = from.plusSeconds(300);

    // Partition 1: checkpoints 1-3, range [1, 3]
    final var metadataSyncer = new BackupMetadataSyncer(backupStore);
    metadataSyncer
        .store(
            1,
            List.of(
                new CheckpointEntry(
                    1, 100, from.minusSeconds(60).toEpochMilli(), CheckpointType.MANUAL_BACKUP, 1),
                new CheckpointEntry(
                    2, 200, from.plusSeconds(60).toEpochMilli(), CheckpointType.MANUAL_BACKUP, 101),
                new CheckpointEntry(
                    3,
                    300,
                    from.plusSeconds(120).toEpochMilli(),
                    CheckpointType.MANUAL_BACKUP,
                    201)),
            List.of(new BackupRange(1, 3)))
        .join();

    // Partition 2: checkpoints 4-6 with non-overlapping IDs, range [4, 6]
    metadataSyncer.store(
        2,
        List.of(
            new CheckpointEntry(
                4, 400, from.minusSeconds(30).toEpochMilli(), CheckpointType.MANUAL_BACKUP, 301),
            new CheckpointEntry(
                5, 500, from.plusSeconds(180).toEpochMilli(), CheckpointType.MANUAL_BACKUP, 401),
            new CheckpointEntry(
                6, 600, from.plusSeconds(240).toEpochMilli(), CheckpointType.MANUAL_BACKUP, 501)),
        List.of(new BackupRange(4, 6)));

    try (final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry())) {

      // when/then - should fail because partitions have no common checkpoints
      assertThatThrownBy(() -> restoreManager.restore(from, to, false, List.of()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Could not find common checkpoint");
    }
  }
}
