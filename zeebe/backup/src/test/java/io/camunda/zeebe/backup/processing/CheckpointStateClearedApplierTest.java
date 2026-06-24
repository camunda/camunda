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
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckpointStateClearedApplierTest {

  @TempDir Path database;
  @AutoClose private ZeebeDb<ZbColumnFamilies> zeebedb;
  private DbCheckpointState checkpointState;
  private DbCheckpointMetadataState checkpointMetadataState;
  private DbBackupRangeState backupRangeState;
  private CheckpointStateClearedApplier applier;

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
    checkpointState = new DbCheckpointState(zeebedb, context);
    checkpointMetadataState = new DbCheckpointMetadataState(zeebedb, context);
    backupRangeState = new DbBackupRangeState(zeebedb, context);
    applier =
        new CheckpointStateClearedApplier(
            checkpointState, checkpointMetadataState, backupRangeState);
  }

  @Test
  void shouldClearLatestCheckpointInfo() {
    // given
    checkpointState.setLatestCheckpointInfo(5L, 50L, 5000L, CheckpointType.MANUAL_BACKUP);

    // when
    applier.apply();

    // then
    assertThat(checkpointState.getLatestCheckpointId()).isEqualTo(-1L);
    assertThat(checkpointState.getLatestCheckpointPosition()).isEqualTo(-1L);
  }

  @Test
  void shouldClearLatestBackupInfo() {
    // given
    checkpointState.setLatestBackupInfo(3L, 30L, 3000L, CheckpointType.SCHEDULED_BACKUP, 20L);

    // when
    applier.apply();

    // then
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(-1L);
    assertThat(checkpointState.getLatestBackupPosition()).isEqualTo(-1L);
  }

  @Test
  void shouldClearAllCheckpointMetadataEntries() {
    // given
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        2L, 200L, 2000L, CheckpointType.MANUAL_BACKUP, 100L);
    checkpointMetadataState.addBackupCheckpoint(
        3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 200L);

    // when
    applier.apply();

    // then
    assertThat(checkpointMetadataState.getCheckpoint(1L)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(2L)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(3L)).isNull();
  }

  @Test
  void shouldClearAllBackupRanges() {
    // given
    backupRangeState.startNewRange(1L);
    backupRangeState.updateRangeEnd(1L, 3L);

    // when
    applier.apply();

    // then
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldClearAllStateComponentsAtOnce() {
    // given — populate all state components
    checkpointState.setLatestCheckpointInfo(10L, 100L, 10000L, CheckpointType.MANUAL_BACKUP);
    checkpointState.setLatestBackupInfo(8L, 80L, 8000L, CheckpointType.SCHEDULED_BACKUP, 70L);

    checkpointMetadataState.addBackupCheckpoint(5L, 50L, 5000L, CheckpointType.MANUAL_BACKUP, 40L);
    checkpointMetadataState.addBackupCheckpoint(
        8L, 80L, 8000L, CheckpointType.SCHEDULED_BACKUP, 70L);
    checkpointMetadataState.addMarkerCheckpoint(10L, 100L, 10000L);

    backupRangeState.startNewRange(5L);
    backupRangeState.updateRangeEnd(5L, 8L);

    // when
    applier.apply();

    // then — all state is cleared
    assertThat(checkpointState.getLatestCheckpointId()).isEqualTo(-1L);
    assertThat(checkpointState.getLatestCheckpointPosition()).isEqualTo(-1L);
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(-1L);
    assertThat(checkpointState.getLatestBackupPosition()).isEqualTo(-1L);

    assertThat(checkpointMetadataState.getCheckpoint(5L)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(8L)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(10L)).isNull();

    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldHandleAlreadyEmptyState() {
    // given — state is already empty (no checkpoint, no backup, no metadata, no ranges)

    // when — should not throw
    applier.apply();

    // then — state remains in initial/empty state
    assertThat(checkpointState.getLatestCheckpointId()).isEqualTo(-1L);
    assertThat(checkpointState.getLatestBackupId()).isEqualTo(-1L);
    assertThat(backupRangeState.getAllRanges()).isEmpty();
  }

  @Test
  void shouldClearMultipleRanges() {
    // given — two separate ranges
    backupRangeState.startNewRange(1L);
    backupRangeState.updateRangeEnd(1L, 3L);

    // Simulate a gap by creating a second range starting at 5
    checkpointMetadataState.addBackupCheckpoint(
        1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    checkpointMetadataState.addBackupCheckpoint(
        3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 200L);
    checkpointMetadataState.addBackupCheckpoint(
        5L, 500L, 5000L, CheckpointType.MANUAL_BACKUP, 400L);

    // when
    applier.apply();

    // then — all ranges and metadata are cleared
    assertThat(backupRangeState.getAllRanges()).isEmpty();
    assertThat(checkpointMetadataState.getCheckpoint(1L)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(3L)).isNull();
    assertThat(checkpointMetadataState.getCheckpoint(5L)).isNull();
  }
}
