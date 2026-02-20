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
  void shouldAddCheckpoint() {
    // when
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);

    // then
    final var checkpoint = state.getCheckpoint(1L);
    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.getCheckpointPosition()).isEqualTo(100L);
    assertThat(checkpoint.getCheckpointTimestamp()).isEqualTo(1000L);
    assertThat(checkpoint.getCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(checkpoint.getFirstLogPosition()).isEqualTo(-1L);
    assertThat(checkpoint.getNumberOfPartitions()).isEqualTo(-1);
    assertThat(checkpoint.getBrokerVersion()).isEmpty();
  }

  @Test
  void shouldAddMultipleCheckpoints() {
    // when
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);
    state.addCheckpoint(2L, 200L, 2000L, CheckpointType.MARKER);
    state.addCheckpoint(3L, 300L, 3000L, CheckpointType.MANUAL_BACKUP);

    // then
    final var all = state.getAllCheckpoints();
    assertThat(all).hasSize(3);
    assertThat(all.get(0).checkpointId()).isEqualTo(1L);
    assertThat(all.get(1).checkpointId()).isEqualTo(2L);
    assertThat(all.get(2).checkpointId()).isEqualTo(3L);
  }

  @Test
  void shouldEnrichWithBackupInfo() {
    // given
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);

    // when
    state.enrichWithBackupInfo(1L, 50L, 3, "8.7.0");

    // then
    final var checkpoint = state.getCheckpoint(1L);
    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.getFirstLogPosition()).isEqualTo(50L);
    assertThat(checkpoint.getNumberOfPartitions()).isEqualTo(3);
    assertThat(checkpoint.getBrokerVersion()).isEqualTo("8.7.0");
    // Original fields preserved
    assertThat(checkpoint.getCheckpointPosition()).isEqualTo(100L);
    assertThat(checkpoint.getCheckpointTimestamp()).isEqualTo(1000L);
    assertThat(checkpoint.getCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldCreateEntryOnEnrichWhenMissing() {
    // when — enriching a non-existent checkpoint (pre-migration scenario)
    state.enrichWithBackupInfo(5L, 50L, 3, "8.7.0");

    // then
    final var checkpoint = state.getCheckpoint(5L);
    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.getFirstLogPosition()).isEqualTo(50L);
    assertThat(checkpoint.getNumberOfPartitions()).isEqualTo(3);
    assertThat(checkpoint.getBrokerVersion()).isEqualTo("8.7.0");
    assertThat(checkpoint.getCheckpointType()).isEqualTo(CheckpointType.MANUAL_BACKUP);
  }

  @Test
  void shouldHandleNullBrokerVersionOnEnrich() {
    // given
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);

    // when
    state.enrichWithBackupInfo(1L, 50L, 3, null);

    // then
    final var checkpoint = state.getCheckpoint(1L);
    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.getBrokerVersion()).isEmpty();
  }

  @Test
  void shouldRemoveCheckpoint() {
    // given
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);

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
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);

    // when/then
    assertThat(state.isEmpty()).isFalse();
  }

  @Test
  void shouldFindPredecessorBackupCheckpoint() {
    // given — 3 backups with a MARKER in between
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);
    state.addCheckpoint(2L, 200L, 2000L, CheckpointType.MARKER);
    state.addCheckpoint(3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP);

    // when — looking for predecessor of checkpoint 3
    final var predecessor = state.findPredecessorBackupCheckpoint(3L);

    // then — should skip MARKER (2) and return SCHEDULED_BACKUP (1)
    assertThat(predecessor).isPresent().hasValue(1L);
  }

  @Test
  void shouldReturnEmptyWhenNoPredecessor() {
    // given
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);

    // when
    final var predecessor = state.findPredecessorBackupCheckpoint(1L);

    // then
    assertThat(predecessor).isEmpty();
  }

  @Test
  void shouldSkipMarkersWhenFindingPredecessor() {
    // given — all MARKERs before the target
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.MARKER);
    state.addCheckpoint(2L, 200L, 2000L, CheckpointType.MARKER);
    state.addCheckpoint(3L, 300L, 3000L, CheckpointType.SCHEDULED_BACKUP);

    // when
    final var predecessor = state.findPredecessorBackupCheckpoint(3L);

    // then — no backup-type predecessors
    assertThat(predecessor).isEmpty();
  }

  @Test
  void shouldFindSuccessorBackupCheckpoint() {
    // given — 3 checkpoints with MARKER in between
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);
    state.addCheckpoint(2L, 200L, 2000L, CheckpointType.MARKER);
    state.addCheckpoint(3L, 300L, 3000L, CheckpointType.MANUAL_BACKUP);

    // when — looking for successor of checkpoint 1
    final var successor = state.findSuccessorBackupCheckpoint(1L);

    // then — should skip MARKER (2) and return MANUAL_BACKUP (3)
    assertThat(successor).isPresent().hasValue(3L);
  }

  @Test
  void shouldReturnEmptyWhenNoSuccessor() {
    // given
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);

    // when
    final var successor = state.findSuccessorBackupCheckpoint(1L);

    // then
    assertThat(successor).isEmpty();
  }

  @Test
  void shouldSkipMarkersWhenFindingSuccessor() {
    // given — all MARKERs after the target
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);
    state.addCheckpoint(2L, 200L, 2000L, CheckpointType.MARKER);
    state.addCheckpoint(3L, 300L, 3000L, CheckpointType.MARKER);

    // when
    final var successor = state.findSuccessorBackupCheckpoint(1L);

    // then — no backup-type successors
    assertThat(successor).isEmpty();
  }

  @Test
  void shouldGetAllCheckpointsInOrder() {
    // given
    state.addCheckpoint(3L, 300L, 3000L, CheckpointType.MANUAL_BACKUP);
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);
    state.addCheckpoint(2L, 200L, 2000L, CheckpointType.MARKER);

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
    state.addCheckpoint(1L, 100L, 1000L, CheckpointType.SCHEDULED_BACKUP);
    state.enrichWithBackupInfo(1L, 50L, 3, "8.7.0");

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
    assertThat(entry.numberOfPartitions()).isEqualTo(3);
    assertThat(entry.brokerVersion()).isEqualTo("8.7.0");
  }
}
