/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import static io.camunda.zeebe.backup.processing.state.CheckpointState.NO_CHECKPOINT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DbCheckpointStateTest {

  @TempDir Path database;
  private DbCheckpointState state;
  private ZeebeDb zeebedb;

  @AfterEach
  void closeDb() throws Exception {
    zeebedb.close();
  }

  @BeforeEach
  void before() {
    zeebedb =
        new ZeebeRocksDbFactory<>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(database.toFile());
    state = new DbCheckpointState(zeebedb, zeebedb.createContext());
  }

  @Test
  void shouldInitializeWhenNoCheckpoint() {
    // given

    // when-then
    assertThat(state.getLatestCheckpointId()).isEqualTo(NO_CHECKPOINT);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(NO_CHECKPOINT);
    assertThat(state.getLatestCheckpointTimestamp()).isEqualTo(Instant.MIN);
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.NONE);
  }

  @Test
  void shouldSetAndGetLatestCheckpointInfo() {
    // when
    final var timestamp = Instant.now();
    state.setLatestCheckpointInfo(5L, 10L, timestamp, CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(state.getLatestCheckpointTimestamp().toEpochMilli())
        .isEqualTo(timestamp.toEpochMilli());
  }

  @Test
  void shouldOverwriteCheckpointInfo() {
    // given
    final var tsBefore = Instant.now();
    state.setLatestCheckpointInfo(5L, 10L, tsBefore, CheckpointType.MARKER);

    // when
    final var tsAfter = Instant.now();
    state.setLatestCheckpointInfo(15L, 20L, tsAfter, CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(15L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(20L);
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(state.getLatestCheckpointTimestamp().toEpochMilli())
        .isEqualTo(tsAfter.toEpochMilli());
  }

  @Test
  void shouldReturnInitialValuesWhenNoBackup() {
    // given

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(NO_CHECKPOINT);
    assertThat(state.getLatestBackupPosition()).isEqualTo(NO_CHECKPOINT);
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(Instant.MIN);
    assertThat(state.getLatestBackupType()).isEqualTo(CheckpointType.NONE);
  }

  @Test
  void shouldSetAndGetLatestBackupIdAndPosition() {
    // when
    final var timestamp = Instant.now();
    state.setLatestBackupInfo(7L, 14L, timestamp, CheckpointType.MARKER);

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
    assertThat(state.getLatestBackupTimestamp().toEpochMilli()).isEqualTo(timestamp.toEpochMilli());
  }

  @Test
  void shouldOverwriteBackupInfo() {
    // given
    final var tsBefore = Instant.now();
    state.setLatestBackupInfo(7L, 14L, tsBefore, CheckpointType.MARKER);

    // when
    final var tsAfter = Instant.now();
    state.setLatestBackupInfo(21L, 28L, tsAfter, CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(21L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(28L);
    assertThat(state.getLatestBackupTimestamp().toEpochMilli()).isEqualTo(tsAfter.toEpochMilli());
  }

  @Test
  void shouldStoreCheckpointAndBackupInfoIndependently() {
    // when
    final var tsCheckpoint = Instant.now();
    final var tsBackup = Instant.now();
    state.setLatestCheckpointInfo(5L, 10L, tsCheckpoint, CheckpointType.MARKER);
    state.setLatestBackupInfo(7L, 14L, tsBackup, CheckpointType.MANUAL_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
    assertThat(state.getLatestCheckpointTimestamp().toEpochMilli())
        .isEqualTo(tsCheckpoint.toEpochMilli());
    assertThat(state.getLatestBackupTimestamp().toEpochMilli()).isEqualTo(tsBackup.toEpochMilli());
  }

  @Test
  void shouldUpdateCheckpointInfoWithoutAffectingBackupInfo() {
    // given
    state.setLatestCheckpointInfo(5L, 10L, Instant.now(), CheckpointType.MARKER);
    state.setLatestBackupInfo(7L, 14L, Instant.now(), CheckpointType.MANUAL_BACKUP);

    // when
    state.setLatestCheckpointInfo(15L, 20L, Instant.now(), CheckpointType.MARKER);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(15L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(20L);
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
  }

  @Test
  void shouldUpdateBackupInfoWithoutAffectingCheckpointInfo() {
    // given
    state.setLatestCheckpointInfo(5L, 10L, Instant.now(), CheckpointType.MARKER);
    state.setLatestBackupInfo(7L, 14L, Instant.now(), CheckpointType.MANUAL_BACKUP);

    // when
    state.setLatestBackupInfo(21L, 28L, Instant.now(), CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestBackupId()).isEqualTo(21L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(28L);
  }
}
