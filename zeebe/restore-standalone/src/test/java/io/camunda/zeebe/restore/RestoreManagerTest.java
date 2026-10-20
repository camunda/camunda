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

import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.Checkpoint;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.history.RestoredHistory;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RestoreManagerTest {

  private static final UUID BUCKET_HISTORY =
      UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID RESTORED_HISTORY =
      UUID.fromString("00000000-0000-4000-8000-000000000002");

  @Test
  void shouldRestoreFromTheBucketAndLeaveTheHistoryThatTakesItOver(
      @TempDir final Path dir, @TempDir final Path bucket, @TempDir final Path journal)
      throws Exception {
    // given: ten entries of two records each, without a snapshot
    final var configuration = bucketRestore(dir, bucket);
    final var fixture = new BucketFixture(new FileSystemStore(bucket));
    fixture.history(BUCKET_HISTORY, Instant.EPOCH, Base.NOTHING);
    try (final var entries = new RaftEntries(journal, 1)) {
      for (int i = 0; i < 10; i++) {
        entries.records();
      }
      fixture.segment(BUCKET_HISTORY, 0, entries.entries());
    }

    // when
    try (final var manager = new RestoreManager(configuration, null, new SimpleMeterRegistry())) {
      manager.restoreFromBucket();
    }

    // then
    final var restored = RestoredHistory.readFrom(dir.resolve("default/partitions/1"));
    assertThat(restored)
        .hasValueSatisfying(
            history -> {
              assertThat(history.history()).isEqualTo(RESTORED_HISTORY);
              assertThat(history.source()).isEqualTo(BUCKET_HISTORY);
              assertThat(history.index()).isEqualTo(10);
              assertThat(history.position()).isEqualTo(20);
            });
  }

  @Test
  void shouldRestoreFromTheBucketInsteadOfTheBackupsWhenConfigured(
      @TempDir final Path dir, @TempDir final Path bucket, @TempDir final Path journal)
      throws Exception {
    // given
    final var configuration = bucketRestore(dir, bucket);
    configuration.getExperimental().getEventPlane().setRestoreTo(6L);
    final var fixture = new BucketFixture(new FileSystemStore(bucket));
    fixture.history(BUCKET_HISTORY, Instant.EPOCH, Base.NOTHING);
    try (final var entries = new RaftEntries(journal, 1)) {
      for (int i = 0; i < 10; i++) {
        entries.records();
      }
      fixture.segment(BUCKET_HISTORY, 0, entries.entries());
    }

    // when: the backup ids name no backup, as the restore app passes none
    try (final var manager = new RestoreManager(configuration, null, new SimpleMeterRegistry())) {
      manager.restore(Map.of(1, new long[0]), false);
    }

    // then
    assertThat(RestoredHistory.readFrom(dir.resolve("default/partitions/1")))
        .hasValueSatisfying(
            history -> {
              assertThat(history.index()).isEqualTo(3);
              assertThat(history.position()).isEqualTo(6);
            });
  }

  private static BrokerCfg bucketRestore(final Path dataDirectory, final Path bucket) {
    final var configuration = new BrokerCfg();
    configuration.getData().setDirectory(dataDirectory.toString());
    configuration.getCluster().setPartitionsCount(1);
    final var eventPlane = configuration.getExperimental().getEventPlane();
    eventPlane.setRestoreFromBucket(true);
    eventPlane.setRestoreHistory(RESTORED_HISTORY.toString());
    eventPlane.getStore().setPath(bucket.toString());
    return configuration;
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
      assertThatThrownBy(() -> restoreManager.restore(from, to, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Could not find common checkpoint");
    }
  }
}
