/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DbCheckpointMetadataStateTest {

  @TempDir Path database;
  private ZeebeDb<ZbColumnFamilies> zeebedb;
  private DbCheckpointMetadataState state;

  @BeforeEach
  void before() {
    zeebedb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(database.toFile());
    state = new DbCheckpointMetadataState(zeebedb, zeebedb.createContext());
  }

  @AfterEach
  void closeDb() throws Exception {
    zeebedb.close();
  }

  @Test
  void shouldAddMarkerCheckpoint() {
    // when
    state.addMarkerCheckpoint(1L, 100L, 1000L);

    // then
    final var checkpoint = state.getCheckpoint(1L);
    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.getCheckpointPosition()).isEqualTo(100L);
    assertThat(checkpoint.getCheckpointTimestamp()).isEqualTo(1000L);
    assertThat(checkpoint.getCheckpointType()).isEqualTo(CheckpointType.MARKER);
    assertThat(checkpoint.getFirstLogPosition()).isEqualTo(-1L);
  }

  @Test
  void shouldAddMultipleCheckpoints() {
    // when
    state.addMarkerCheckpoint(1L, 100L, 1000L);
    state.addMarkerCheckpoint(2L, 200L, 2000L);
    state.addMarkerCheckpoint(3L, 300L, 3000L);

    // then
    final var all = state.getAllCheckpoints();
    assertThat(all).hasSize(3);
    assertThat(all.get(0).checkpointId()).isEqualTo(1L);
    assertThat(all.get(1).checkpointId()).isEqualTo(2L);
    assertThat(all.get(2).checkpointId()).isEqualTo(3L);
  }

  @Test
  void shouldAddBackupCheckpoint() {
    // when
    state.addBackupCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);

    // then
    final var checkpoint = state.getCheckpoint(1L);
    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.getFirstLogPosition()).isEqualTo(50L);
    assertThat(checkpoint.getCheckpointPosition()).isEqualTo(100L);
    assertThat(checkpoint.getCheckpointTimestamp()).isEqualTo(1000L);
    assertThat(checkpoint.getCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldRemoveCheckpoint() {
    // given
    state.addMarkerCheckpoint(1L, 100L, 1000L);

    // when
    state.removeCheckpoint(1L);

    // then
    assertThat(state.getCheckpoint(1L)).isNull();
  }

  @Test
  void shouldRemoveNonExistentCheckpointSafely() {
    // when/then — should not throw
    state.removeCheckpoint(99L);
  }

  @Test
  void shouldReturnNullForNonExistentCheckpoint() {
    // when/then
    assertThat(state.getCheckpoint(99L)).isNull();
  }

  @Test
  void shouldReportEmptyWhenNoCheckpoints() {
    // when/then
    assertThat(state.isEmpty()).isTrue();
  }

  @Test
  void shouldReportNotEmptyAfterAddingCheckpoint() {
    // given
    state.addMarkerCheckpoint(1L, 100L, 1000L);

    // when/then
    assertThat(state.isEmpty()).isFalse();
  }

  @Test
  void shouldFindPredecessorBackupCheckpoint() {
    // given — 3 backups with a MARKER in between
    state.addBackupCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    state.addMarkerCheckpoint(2L, 200L, 2000L);
    state.addBackupCheckpoint(3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 150L);

    // when — looking for predecessor of checkpoint 3
    final var predecessor = state.findPredecessorBackupCheckpoint(3L);

    // then — should skip MARKER (2) and return SCHEDULED_BACKUP (1)
    assertThat(predecessor).isPresent().hasValue(1L);
  }

  @Test
  void shouldReturnEmptyWhenNoPredecessor() {
    // given
    state.addMarkerCheckpoint(1L, 100L, 1000L);

    // when
    final var predecessor = state.findPredecessorBackupCheckpoint(1L);

    // then
    assertThat(predecessor).isEmpty();
  }

  @Test
  void shouldSkipMarkersWhenFindingPredecessor() {
    // given — all MARKERs before the target
    state.addMarkerCheckpoint(1L, 100L, 1000L);
    state.addMarkerCheckpoint(2L, 200L, 2000L);
    state.addBackupCheckpoint(3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 100L);

    // when
    final var predecessor = state.findPredecessorBackupCheckpoint(3L);

    // then — no backup-type predecessors
    assertThat(predecessor).isEmpty();
  }

  @Test
  void shouldFindSuccessorBackupCheckpoint() {
    // given — 3 checkpoints with MARKER in between
    state.addBackupCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    state.addMarkerCheckpoint(2L, 200L, 2000L);
    state.addBackupCheckpoint(3L, 300L, 3000L, CheckpointType.MANUAL_BACKUP, 250L);

    // when — looking for successor of checkpoint 1
    final var successor = state.findSuccessorBackupCheckpoint(1L);

    // then — should skip MARKER (2) and return MANUAL_BACKUP (3)
    assertThat(successor).isPresent().hasValue(3L);
  }

  @Test
  void shouldReturnEmptyWhenNoSuccessor() {
    // given
    state.addMarkerCheckpoint(1L, 100L, 1000L);

    // when
    final var successor = state.findSuccessorBackupCheckpoint(1L);

    // then
    assertThat(successor).isEmpty();
  }

  @Test
  void shouldSkipMarkersWhenFindingSuccessor() {
    // given — all MARKERs after the target
    state.addBackupCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    state.addMarkerCheckpoint(2L, 200L, 2000L);
    state.addMarkerCheckpoint(3L, 300L, 3000L);

    // when
    final var successor = state.findSuccessorBackupCheckpoint(1L);

    // then — no backup-type successors
    assertThat(successor).isEmpty();
  }

  @Test
  void shouldGetAllCheckpointsInOrder() {
    // given
    state.addMarkerCheckpoint(3L, 300L, 3000L);
    state.addMarkerCheckpoint(1L, 100L, 1000L);
    state.addMarkerCheckpoint(2L, 200L, 2000L);

    // when
    final var all = state.getAllCheckpoints();

    // then — ordered by checkpoint ID (key ordering)
    assertThat(all).hasSize(3);
    assertThat(all.get(0).checkpointId()).isEqualTo(1L);
    assertThat(all.get(1).checkpointId()).isEqualTo(2L);
    assertThat(all.get(2).checkpointId()).isEqualTo(3L);
  }

  @Test
  void shouldReturnEmptyListWhenNoCheckpoints() {
    // when/then
    assertThat(state.getAllCheckpoints()).isEmpty();
  }

  @Test
  void shouldPreserveAllFieldsInGetAllCheckpoints() {
    // given
    state.addBackupCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);

    // when
    final var all = state.getAllCheckpoints();

    // then
    assertThat(all).hasSize(1);
    final var entry = all.get(0);
    assertThat(entry.checkpointId()).isEqualTo(1L);
    assertThat(entry.checkpointPosition()).isEqualTo(100L);
    assertThat(entry.checkpointTimestamp()).isEqualTo(1000L);
    assertThat(entry.checkpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(entry.firstLogPosition()).isEqualTo(50L);
  }

  // --- removeCheckpointsUntil ---

  @Test
  void shouldRemoveCheckpointsWithLogPositionBeforeThreshold() {
    // given — checkpoints with different firstLogPositions
    state.addBackupCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 10L);
    state.addBackupCheckpoint(2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 20L);
    state.addBackupCheckpoint(3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP, 50L);

    // when — remove all with firstLogPosition < 50
    state.removeCheckpointsUntil(50L);

    // then — only checkpoint 3 remains
    final var all = state.getAllCheckpoints();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).checkpointId()).isEqualTo(3L);
  }

  @Test
  void shouldNotRemoveCheckpointsAtOrAboveThreshold() {
    // given
    state.addBackupCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP, 50L);
    state.addBackupCheckpoint(2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 100L);

    // when — threshold equals the first checkpoint's log position
    state.removeCheckpointsUntil(50L);

    // then — both remain (50 is not < 50)
    assertThat(state.getAllCheckpoints()).hasSize(2);
  }

  @Test
  void shouldHandleEmptyStateForRemoveCheckpointsUntil() {
    // when/then — should not throw
    state.removeCheckpointsUntil(100L);
    assertThat(state.getAllCheckpoints()).isEmpty();
  }

  @Test
  void shouldRemoveMarkersWithLogPositionBeforeThreshold() {
    // given — markers have firstLogPosition of -1
    state.addMarkerCheckpoint(1L, 100L, 1000L);
    state.addBackupCheckpoint(2L, 200L, 2000L, CheckpointType.SCHEDULED_BACKUP, 50L);

    // when — remove all with firstLogPosition < 50 (marker has -1)
    state.removeCheckpointsUntil(50L);

    // then — marker removed, backup remains
    final var all = state.getAllCheckpoints();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).checkpointId()).isEqualTo(2L);
  }
}
