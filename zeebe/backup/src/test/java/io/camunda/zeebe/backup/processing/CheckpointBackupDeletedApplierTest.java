/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState.BackupRange;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckpointBackupDeletedApplierTest {

  @TempDir Path database;
  @AutoClose private ZeebeDb<ZbColumnFamilies> zeebedb;
  private DbCheckpointMetadataState checkpointMetadataState;
  private DbBackupRangeState backupRangeState;
  private CheckpointBackupDeletedApplier applier;
  private DbCheckpointState checkpointState;

  @BeforeEach
  void before() {
    zeebedb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(database.toFile());
    final var context = zeebedb.createContext();
    checkpointMetadataState = new DbCheckpointMetadataState(zeebedb, context);
    backupRangeState = new DbBackupRangeState(zeebedb, context);
    checkpointState = new DbCheckpointState(zeebedb, context);
    applier =
        new CheckpointBackupDeletedApplier(
            checkpointMetadataState, backupRangeState, checkpointState);
  }

  @Test
  void shouldRemoveCheckpointFromState() {
    // given
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    backupRangeState.startNewRange(1L);

    // when
    applier.apply(checkpointRecord(1L));

    // then
    assertThat(checkpointMetadataState.getCheckpoint(1L)).isNull();
  }

  @Test
  void shouldDeleteSingleEntryRange() {
    // given — single checkpoint in a range
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    backupRangeState.startNewRange(1L);

    // when
    applier.apply(checkpointRecord(1L));

    // then — range is deleted
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldAdvanceRangeStartWhenDeletingFirstCheckpoint() {
    // given — range [1, 3] with checkpoints 1, 2, 3
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);
    checkpointMetadataState.addBackupCheckpoint(
        3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 200L);
    backupRangeState.startNewRange(1L);
    backupRangeState.updateRangeEnd(1L, 3L);

    // when — delete checkpoint 1
    applier.apply(checkpointRecord(1L));

    // then — range advanced to [2, 3]
    assertThat(backupRangeState.getAllRanges()).containsExactly(new BackupRange(2L, 3L));
  }

  @Test
  void shouldShrinkRangeEndWhenDeletingLastCheckpoint() {
    // given — range [1, 3] with checkpoints 1, 2, 3
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);
    checkpointMetadataState.addBackupCheckpoint(
        3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 200L);
    backupRangeState.startNewRange(1L);
    backupRangeState.updateRangeEnd(1L, 3L);

    // when — delete checkpoint 3
    applier.apply(checkpointRecord(3L));

    // then — range shrunk to [1, 2]
    assertThat(backupRangeState.getAllRanges()).containsExactly(new BackupRange(1L, 2L));
  }

  @Test
  void shouldSplitRangeWhenDeletingMiddleCheckpoint() {
    // given — range [1, 3] with checkpoints 1, 2, 3
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);
    checkpointMetadataState.addBackupCheckpoint(
        3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 200L);
    backupRangeState.startNewRange(1L);
    backupRangeState.updateRangeEnd(1L, 3L);

    // when — delete checkpoint 2
    applier.apply(checkpointRecord(2L));

    // then — split into [1, 1] and [3, 3]
    assertThat(backupRangeState.getAllRanges())
        .containsExactly(new BackupRange(1L, 1L), new BackupRange(3L, 3L));
  }

  @Test
  void shouldSkipRangeMaintenanceWhenCheckpointNotInAnyRange() {
    // given — checkpoint exists but is not in any range
    checkpointMetadataState.addBackupCheckpoint(
        5L, 500L, 5000L, CheckpointType.SCHEDULED_BACKUP, 400L);

    // when — delete checkpoint not in any range
    applier.apply(checkpointRecord(5L));

    // then — checkpoint removed, no range changes
    assertThat(checkpointMetadataState.getCheckpoint(5L)).isNull();
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldRemoveUncoveredMarkersAfterDeletion() {
    // given — marker with low log position, then backup checkpoints in a range
    checkpointMetadataState.addMarkerCheckpoint(1L, 50L, 500L); // firstLogPosition = -1
    checkpointMetadataState.addBackupCheckpoint(
        2L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        3L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);
    backupRangeState.startNewRange(2L);
    backupRangeState.updateRangeEnd(2L, 3L);

    // when — delete checkpoint 2 (start of range), range advances to [3, 3]
    applier.apply(checkpointRecord(2L));

    // then — marker (checkpoint 1) with firstLogPosition -1 < 100 is removed
    assertThat(checkpointMetadataState.getCheckpoint(1L)).isNull();
    // checkpoint 3 remains
    assertThat(checkpointMetadataState.getCheckpoint(3L)).isNotNull();
  }

  @Test
  void shouldRemoveCheckpointBeforeRangeUpdate() {
    // given — range [1, 1] with single checkpoint
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    backupRangeState.startNewRange(1L);

    // when — apply deletion
    applier.apply(checkpointRecord(1L));

    // then — both checkpoint and range are cleaned up
    assertThat(checkpointMetadataState.getCheckpoint(1L)).isNull();
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldClearLatestBackupInfoWhenDeletingLatestWithNoPredecessor() {
    // given — single checkpoint that is also the latest backup
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    backupRangeState.startNewRange(1L);
    checkpointState.setLatestBackupInfo(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);

    // when
    applier.apply(checkpointRecord(1L));

    // then — latest backup info is cleared
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(-1L);
  }

  @Test
  void shouldRollBackLatestBackupInfoToPredecessor() {
    // given — two checkpoints, latest backup points to checkpoint 2
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);
    backupRangeState.startNewRange(1L);
    backupRangeState.updateRangeEnd(1L, 2L);
    checkpointState.setLatestBackupInfo(2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);

    // when — delete checkpoint 2 (the latest)
    applier.apply(checkpointRecord(2L));

    // then — latest backup info rolled back to checkpoint 1
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(1L);
    assertThat(checkpointState.getLatestBackupPosition()).isEqualTo(100L);
    assertThat(checkpointState.getLatestBackupFirstLogPosition()).isEqualTo(50L);
  }

  @Test
  void shouldNotChangeLatestBackupInfoWhenDeletingNonLatestCheckpoint() {
    // given — two checkpoints, latest backup points to checkpoint 2
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);
    backupRangeState.startNewRange(1L);
    backupRangeState.updateRangeEnd(1L, 2L);
    checkpointState.setLatestBackupInfo(2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);

    // when — delete checkpoint 1 (not the latest)
    applier.apply(checkpointRecord(1L));

    // then — latest backup info unchanged
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(2L);
  }

  private static CheckpointRecord checkpointRecord(final long checkpointId) {
    return new CheckpointRecord().setCheckpointId(checkpointId);
  }
}
