/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import static io.camunda.zeebe.backup.processing.state.CheckpointState.NO_CHECKPOINT;
import static io.camunda.zeebe.backup.processing.state.CheckpointState.NO_TIMESTAMP;
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
import java.time.InstantSource;
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
  }

  @Test
  void shouldSetAndGetLatestCheckpointInfo() {
    // when
    final var timestamp = InstantSource.system().millis();
    state.setLatestCheckpointInfo(5L, 10L, timestamp, CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(state.getLatestCheckpointTimestamp()).isEqualTo(timestamp);
  }

  @Test
  void shouldOverwriteCheckpointInfo() {
    // given
    final var tsBefore = InstantSource.system().millis();
    state.setLatestCheckpointInfo(5L, 10L, tsBefore, CheckpointType.NO_BACKUP);

    // when
    final var tsAfter = InstantSource.system().millis();
    state.setLatestCheckpointInfo(15L, 20L, tsAfter, CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(15L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(20L);
    assertThat(state.getLatestCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
    assertThat(state.getLatestCheckpointTimestamp()).isEqualTo(tsAfter);
  }

  @Test
  void shouldReturnInitialValuesWhenNoBackup() {
    // given

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(NO_CHECKPOINT);
    assertThat(state.getLatestBackupPosition()).isEqualTo(NO_CHECKPOINT);
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(NO_TIMESTAMP);
    assertThat(state.getLatestBackupType()).isEqualTo(CheckpointType.NONE);
  }

  @Test
  void shouldSetAndGetLatestBackupIdAndPosition() {
    // when
    final var timestamp = InstantSource.system().millis();
    state.setLatestBackupInfo(7L, 14L, timestamp, CheckpointType.NO_BACKUP);

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(timestamp);
  }

  @Test
  void shouldOverwriteBackupInfo() {
    // given
    final var tsBefore = InstantSource.system().millis();
    state.setLatestBackupInfo(7L, 14L, tsBefore, CheckpointType.NO_BACKUP);

    // when
    final var tsAfter = InstantSource.system().millis();
    state.setLatestBackupInfo(21L, 28L, tsAfter, CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(21L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(28L);
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(tsAfter);
  }

  @Test
  void shouldStoreCheckpointAndBackupInfoIndependently() {
    // when
    final var tsCheckpoint = InstantSource.system().millis();
    final var tsBackup = InstantSource.system().millis();
    state.setLatestCheckpointInfo(5L, 10L, tsCheckpoint, CheckpointType.NO_BACKUP);
    state.setLatestBackupInfo(7L, 14L, tsBackup, CheckpointType.MANUAL_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
    assertThat(state.getLatestCheckpointTimestamp()).isEqualTo(tsCheckpoint);
    assertThat(state.getLatestBackupTimestamp()).isEqualTo(tsBackup);
  }

  @Test
  void shouldUpdateCheckpointInfoWithoutAffectingBackupInfo() {
    // given
    state.setLatestCheckpointInfo(
        5L, 10L, InstantSource.system().millis(), CheckpointType.NO_BACKUP);
    state.setLatestBackupInfo(
        7L, 14L, InstantSource.system().millis(), CheckpointType.MANUAL_BACKUP);

    // when
    state.setLatestCheckpointInfo(
        15L, 20L, InstantSource.system().millis(), CheckpointType.NO_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(15L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(20L);
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
  }

  @Test
  void shouldUpdateBackupInfoWithoutAffectingCheckpointInfo() {
    // given
    state.setLatestCheckpointInfo(
        5L, 10L, InstantSource.system().millis(), CheckpointType.NO_BACKUP);
    state.setLatestBackupInfo(
        7L, 14L, InstantSource.system().millis(), CheckpointType.MANUAL_BACKUP);

    // when
    state.setLatestBackupInfo(
        21L, 28L, InstantSource.system().millis(), CheckpointType.SCHEDULED_BACKUP);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestBackupId()).isEqualTo(21L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(28L);
  }
}
