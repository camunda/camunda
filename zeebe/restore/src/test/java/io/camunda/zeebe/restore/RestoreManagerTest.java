/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.Checkpoint;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
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
    final var metadataSyncer = new BackupMetadataSyncer(backupStore, new SimpleMeterRegistry());
    metadataSyncer
        .store(
            1,
            List.of(
                new Checkpoint(
                    CheckpointType.MANUAL_BACKUP, 1, from.minusSeconds(60).toEpochMilli(), 100, 1),
                new Checkpoint(
                    CheckpointType.MANUAL_BACKUP, 2, from.plusSeconds(60).toEpochMilli(), 200, 101),
                new Checkpoint(
                    CheckpointType.MANUAL_BACKUP,
                    3,
                    from.plusSeconds(120).toEpochMilli(),
                    300,
                    201)),
            List.of(new BackupRange(1, 3)))
        .join();

    // Partition 2: checkpoints 4-6 with non-overlapping IDs, range [4, 6]
    metadataSyncer.store(
        2,
        List.of(
            new Checkpoint(
                CheckpointType.MANUAL_BACKUP, 4, from.minusSeconds(30).toEpochMilli(), 400, 301),
            new Checkpoint(
                CheckpointType.MANUAL_BACKUP, 5, from.plusSeconds(180).toEpochMilli(), 500, 401),
            new Checkpoint(
                CheckpointType.MANUAL_BACKUP, 6, from.plusSeconds(240).toEpochMilli(), 600, 501)),
        List.of(new BackupRange(4, 6)));

    try (final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry())) {

      // when/then - should fail because partitions have no common checkpoints
      assertThatThrownBy(() -> restoreManager.restore(from, to, false, List.of()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Could not find common checkpoint");
    }
  }

  @Test
  void shouldResolveThePartitionsOfTheLatestBackup(@TempDir final Path dir) {
    // given - a cluster scaled up to 5 partitions, with backups taken while it had 3
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(5);
    final var backupStore = new TestRestorableBackupStore();
    saveBackup(backupStore, 1, 3);
    saveBackup(backupStore, 2, 3);

    try (final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry())) {

      // when
      final var backupIdsByPartition = restoreManager.resolveBackups(new long[] {1, 2});

      // then - only the backups' partitions are restored
      assertThat(backupIdsByPartition).containsOnlyKeys(1, 2, 3);
      assertThat(backupIdsByPartition.values())
          .allSatisfy(ids -> assertThat(ids).containsExactly(1, 2));
    }
  }

  @Test
  void shouldRejectBackupWithMorePartitionsThanConfigured(@TempDir final Path dir) {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(2);
    final var backupStore = new TestRestorableBackupStore();
    saveBackup(backupStore, 1, 3);

    try (final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry())) {

      // when/then
      assertThatThrownBy(() -> restoreManager.resolveBackups(new long[] {1}))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("it has 3 partitions, but only 2 are configured");
    }
  }

  @Test
  void shouldResolveThePartitionsTheRdbmsHoldsExportedPositionsFor(@TempDir final Path dir) {
    // given - a cluster scaled up to 5 partitions, with an RDBMS restored to backups of 3
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(5);
    final var backupStore = new TestRestorableBackupStore();
    storeCheckpoint(backupStore, 3, 1);
    saveBackup(backupStore, 1, 3);
    final var exporterPositionMapper = exportedPositions(Map.of(1, 50L, 2, 50L, 3, 50L));

    try (final var restoreManager =
        new RestoreManager(
            configuration, backupStore, exporterPositionMapper, new SimpleMeterRegistry())) {

      // when
      final var backupIdsByPartition = restoreManager.resolveBackups(null, null);

      // then
      assertThat(backupIdsByPartition).containsOnlyKeys(1, 2, 3);
    }
  }

  @Test
  void shouldRejectRdbmsWithAGapInTheExportedPositions(@TempDir final Path dir) {
    // given
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(3);
    final var exporterPositionMapper = exportedPositions(Map.of(1, 50L, 3, 50L));

    try (final var restoreManager =
        new RestoreManager(
            configuration,
            new TestRestorableBackupStore(),
            exporterPositionMapper,
            new SimpleMeterRegistry())) {

      // when/then
      assertThatThrownBy(() -> restoreManager.resolveBackups(null, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("exported position for partition 3 but none for partition 2");
    }
  }

  @Test
  void shouldRejectRdbmsHoldingFewerPartitionsThanTheBackup(@TempDir final Path dir) {
    // given - backups of 3 partitions, but exported positions for only 2
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dir.toString());
    configuration.getCluster().setPartitionsCount(3);
    final var backupStore = new TestRestorableBackupStore();
    storeCheckpoint(backupStore, 2, 1);
    saveBackup(backupStore, 1, 3);
    final var exporterPositionMapper = exportedPositions(Map.of(1, 50L, 2, 50L));

    try (final var restoreManager =
        new RestoreManager(
            configuration, backupStore, exporterPositionMapper, new SimpleMeterRegistry())) {

      // when/then
      assertThatThrownBy(() -> restoreManager.resolveBackups(null, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "backup 1 was taken with 3 partitions, but the RDBMS holds exported positions for 2");
    }
  }

  private static void saveBackup(
      final TestRestorableBackupStore backupStore, final long backupId, final int partitionCount) {
    for (int partition = 1; partition <= partitionCount; partition++) {
      backupStore.save(
          new BackupImpl(
              new BackupIdentifierImpl(0, partition, backupId),
              new BackupDescriptorImpl(
                  Optional.empty(),
                  OptionalLong.empty(),
                  100,
                  partitionCount,
                  "8.9.0",
                  Instant.parse("2024-01-01T10:00:00Z"),
                  CheckpointType.MANUAL_BACKUP),
              new NamedFileSetImpl(Map.of()),
              new NamedFileSetImpl(Map.of())));
    }
  }

  /** Records a backup checkpoint at position 100 in the metadata of the first partitions. */
  private static void storeCheckpoint(
      final TestRestorableBackupStore backupStore,
      final int partitionCount,
      final long checkpointId) {
    final var metadataSyncer = new BackupMetadataSyncer(backupStore, new SimpleMeterRegistry());
    for (int partition = 1; partition <= partitionCount; partition++) {
      metadataSyncer
          .store(
              partition,
              List.of(
                  new Checkpoint(
                      CheckpointType.MANUAL_BACKUP,
                      checkpointId,
                      Instant.parse("2024-01-01T10:00:00Z").toEpochMilli(),
                      100,
                      1)),
              List.of(new BackupRange(checkpointId, checkpointId)))
          .join();
    }
  }

  private static ExporterPositionMapper exportedPositions(final Map<Integer, Long> positions) {
    final var mapper = mock(ExporterPositionMapper.class);
    when(mapper.findOne(anyInt()))
        .thenAnswer(
            invocation -> {
              final int partition = invocation.getArgument(0);
              final var position = positions.get(partition);
              return position == null
                  ? null
                  : new ExporterPositionModel(
                      partition, "exporter", position, LocalDateTime.now(), LocalDateTime.now());
            });
    return mapper;
  }
}
