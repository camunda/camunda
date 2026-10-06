/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RestoreManagerTest {

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

    // The latest backup of partition 1 records the partition count to restore
    backupStore.save(
        new BackupImpl(
            new BackupIdentifierImpl(0, 1, 3),
            new BackupDescriptorImpl(
                Optional.empty(),
                OptionalLong.empty(),
                300,
                2,
                "8.10.0",
                from.plusSeconds(120),
                CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of())));

    try (final var restoreManager =
        new RestoreManager(configuration, backupStore, new SimpleMeterRegistry())) {

      // when/then - should fail because partitions have no common checkpoints
      assertThatThrownBy(() -> restoreManager.restore(from, to, false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Could not find common checkpoint");
    }
  }
}
