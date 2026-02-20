/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.processing.state.CheckpointState;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckpointBackupDeletedApplierTest {

  @TempDir Path database;
  private ZeebeDb<ZbColumnFamilies> zeebedb;
  private DbCheckpointMetadataState checkpointMetadataState;
  private DbBackupRangeState backupRangeState;
  private CheckpointState checkpointState;
  private CheckpointBackupDeletedApplier applier;

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

  @AfterEach
  void closeDb() throws Exception {
    zeebedb.close();
  }

  // --- Single-entry range deletion ---

  @Test
  void shouldDeleteSingleEntryRange() {
    // given — range [5, 5] with a single checkpoint
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(checkpointId, 40, 3, "8.9.0");
    backupRangeState.startNewRange(checkpointId);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(checkpointId));

    // then — range is deleted and checkpoint is removed
    assertThat(backupRangeState.getAllRanges()).isEmpty();
    assertThat(checkpointMetadataState.getCheckpoint(checkpointId)).isNull();
  }

  // --- Advance start (delete from start of range) ---

  @Test
  void shouldAdvanceRangeStartWhenDeletingFirstCheckpoint() {
    // given — range [5, 10] with two checkpoints
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(firstId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(secondId, 100, 2000L, CheckpointType.SCHEDULED_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(secondId, 50, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(firstId));

    // then — range is advanced to [10, 10]
    assertThat(backupRangeState.getAllRanges())
        .containsExactly(new BackupRange(secondId, secondId));
    assertThat(checkpointMetadataState.getCheckpoint(firstId)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(secondId)).isNotNull();
  }

  // --- Shrink end (delete from end of range) ---

  @Test
  void shouldShrinkRangeEndWhenDeletingLastCheckpoint() {
    // given — range [5, 10] with two checkpoints
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(firstId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(secondId, 100, 2000L, CheckpointType.SCHEDULED_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(secondId, 50, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(secondId));

    // then — range is shrunk to [5, 5]
    assertThat(backupRangeState.getAllRanges()).containsExactly(new BackupRange(firstId, firstId));
    assertThat(checkpointMetadataState.getCheckpoint(firstId)).isNotNull();
    assertThat(checkpointMetadataState.getCheckpoint(secondId)).isNull();
  }

  // --- Mid-range split ---

  @Test
  void shouldSplitRangeWhenDeletingMiddleCheckpoint() {
    // given — range [5, 15] with three checkpoints
    final var firstId = 5L;
    final var middleId = 10L;
    final var lastId = 15L;
    checkpointMetadataState.addCheckpoint(firstId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(middleId, 100, 2000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(middleId, 50, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(lastId, 150, 3000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(lastId, 100, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, lastId);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(middleId));

    // then — range is split into [5, 5] and [15, 15]
    assertThat(backupRangeState.getAllRanges())
        .containsExactly(new BackupRange(firstId, firstId), new BackupRange(lastId, lastId));
    assertThat(checkpointMetadataState.getCheckpoint(middleId)).isNull();
  }

  // --- Warning/fallback paths ---

  @Test
  void shouldDeleteEntireRangeWhenSuccessorMissingForStartDeletion() {
    // given — range [5, 10] but only checkpoint 5 exists in metadata (successor missing)
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(firstId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    // intentionally NOT adding secondId to metadata — simulates corrupt/inconsistent state
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(firstId));

    // then — entire range is deleted as fallback (no successor found)
    assertThat(backupRangeState.getAllRanges()).isEmpty();
    assertThat(checkpointMetadataState.getCheckpoint(firstId)).isNull();
  }

  @Test
  void shouldDeleteEntireRangeWhenPredecessorMissingForEndDeletion() {
    // given — range [5, 10] but only checkpoint 10 exists in metadata (predecessor missing)
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(secondId, 100, 2000L, CheckpointType.SCHEDULED_BACKUP);
    // intentionally NOT adding firstId to metadata
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(secondId));

    // then — entire range is deleted as fallback (no predecessor found)
    assertThat(backupRangeState.getAllRanges()).isEmpty();
    assertThat(checkpointMetadataState.getCheckpoint(secondId)).isNull();
  }

  @Test
  void shouldLogWarningWhenBothNeighborsMissingForMidRangeDeletion() {
    // given — range [5, 15] but only checkpoint 10 exists (predecessor and successor missing)
    final var firstId = 5L;
    final var middleId = 10L;
    final var lastId = 15L;
    checkpointMetadataState.addCheckpoint(middleId, 100, 2000L, CheckpointType.MANUAL_BACKUP);
    // intentionally NOT adding firstId and lastId to metadata
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, lastId);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(middleId));

    // then — range is unchanged (both predecessor and successor missing, no split possible)
    // The checkpoint is still removed from CHECKPOINTS CF
    assertThat(backupRangeState.getAllRanges()).containsExactly(new BackupRange(firstId, lastId));
    assertThat(checkpointMetadataState.getCheckpoint(middleId)).isNull();
  }

  // --- Checkpoint not in any range ---

  @Test
  void shouldHandleCheckpointNotInAnyRange() {
    // given — checkpoint exists in metadata but not in any range
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);

    // when — should not throw
    applier.apply(new CheckpointRecord().setCheckpointId(checkpointId));

    // then — checkpoint is removed from metadata
    assertThat(checkpointMetadataState.getCheckpoint(checkpointId)).isNull();
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  // --- Legacy state rollback ---

  @Test
  void shouldRollBackLatestBackupToPredecessorOnDeletion() {
    // given — two backups: 5 (older) and 10 (latest)
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(firstId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(secondId, 100, 2000L, CheckpointType.SCHEDULED_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(secondId, 50, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);
    checkpointState.setLatestBackupInfo(secondId, 100, 2000L, CheckpointType.SCHEDULED_BACKUP, 50);

    // when — delete the latest backup
    applier.apply(new CheckpointRecord().setCheckpointId(secondId));

    // then — latest backup rolled back to predecessor
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(firstId);
    assertThat(checkpointState.getLatestBackupPosition()).isEqualTo(50);
    assertThat(checkpointState.getLatestBackupTimestamp()).isEqualTo(1000L);
    assertThat(checkpointState.getLatestBackupType()).isEqualTo(CheckpointType.MANUAL_BACKUP);
    assertThat(checkpointState.getLatestBackupFirstLogPosition()).isEqualTo(40);
  }

  @Test
  void shouldClearLatestBackupWhenDeletingOnlyBackup() {
    // given — single backup
    final var checkpointId = 5L;
    checkpointMetadataState.addCheckpoint(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(checkpointId, 40, 3, "8.9.0");
    backupRangeState.startNewRange(checkpointId);
    checkpointState.setLatestBackupInfo(checkpointId, 50, 1000L, CheckpointType.MANUAL_BACKUP, 40);

    // when
    applier.apply(new CheckpointRecord().setCheckpointId(checkpointId));

    // then — latest backup is cleared
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(CheckpointState.NO_CHECKPOINT);
    assertThat(checkpointState.getLatestBackupPosition()).isEqualTo(CheckpointState.NO_CHECKPOINT);
  }

  @Test
  void shouldNotAffectLatestBackupWhenDeletingOlderCheckpoint() {
    // given — two backups, deleting the older one
    final var firstId = 5L;
    final var secondId = 10L;
    checkpointMetadataState.addCheckpoint(firstId, 50, 1000L, CheckpointType.MANUAL_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(firstId, 40, 3, "8.9.0");
    checkpointMetadataState.addCheckpoint(secondId, 100, 2000L, CheckpointType.SCHEDULED_BACKUP);
    checkpointMetadataState.enrichWithBackupInfo(secondId, 50, 3, "8.9.0");
    backupRangeState.startNewRange(firstId);
    backupRangeState.extendRange(firstId, secondId);
    checkpointState.setLatestBackupInfo(secondId, 100, 2000L, CheckpointType.SCHEDULED_BACKUP, 50);

    // when — delete the older backup
    applier.apply(new CheckpointRecord().setCheckpointId(firstId));

    // then — latest backup is unchanged
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(secondId);
    assertThat(checkpointState.getLatestBackupPosition()).isEqualTo(100);
  }
}
