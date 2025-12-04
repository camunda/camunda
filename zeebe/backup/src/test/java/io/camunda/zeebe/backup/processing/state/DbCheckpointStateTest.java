/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import static io.camunda.zeebe.backup.processing.state.CheckpointState.NO_CHECKPOINT;
import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_CACHE_SIZE;
import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_WRITE_BUFFER_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

final class DbCheckpointStateTest {

  static {
    RocksDB.loadLibrary();
  }

  @TempDir Path database;
  private DbCheckpointState state;
  private ZeebeDb zeebedb;

  @AfterEach
  void closeDb() throws Exception {
    zeebedb.close();
  }

  @BeforeEach
  void before() {
    final LRUCache lruCache = new LRUCache(DEFAULT_CACHE_SIZE);
    final int defaultPartitionCount = 3;
    zeebedb =
        new ZeebeRocksDbFactory<>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new,
                lruCache,
                new WriteBufferManager(DEFAULT_WRITE_BUFFER_SIZE, lruCache),
                defaultPartitionCount)
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
  void shouldSetAndGetLatestCheckpointIdAndPosition() {
    // when
    state.setLatestCheckpointInfo(5L, 10L);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
  }

  @Test
  void shouldOverwriteCheckpointIdAndPosition() {
    // given
    state.setLatestCheckpointInfo(5L, 10L);

    // when
    state.setLatestCheckpointInfo(15L, 20L);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(15L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(20L);
  }

  @Test
  void shouldReturnInitialValuesWhenNoBackup() {
    // given

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(NO_CHECKPOINT);
    assertThat(state.getLatestBackupPosition()).isEqualTo(NO_CHECKPOINT);
  }

  @Test
  void shouldSetAndGetLatestBackupIdAndPosition() {
    // when
    state.setLatestBackupInfo(7L, 14L);

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
  }

  @Test
  void shouldOverwriteBackupIdAndPosition() {
    // given
    state.setLatestBackupInfo(7L, 14L);

    // when
    state.setLatestBackupInfo(21L, 28L);

    // then
    assertThat(state.getLatestBackupId()).isEqualTo(21L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(28L);
  }

  @Test
  void shouldStoreCheckpointAndBackupInfoIndependently() {
    // when
    state.setLatestCheckpointInfo(5L, 10L);
    state.setLatestBackupInfo(7L, 14L);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
  }

  @Test
  void shouldUpdateCheckpointInfoWithoutAffectingBackupInfo() {
    // given
    state.setLatestCheckpointInfo(5L, 10L);
    state.setLatestBackupInfo(7L, 14L);

    // when
    state.setLatestCheckpointInfo(15L, 20L);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(15L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(20L);
    assertThat(state.getLatestBackupId()).isEqualTo(7L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(14L);
  }

  @Test
  void shouldUpdateBackupInfoWithoutAffectingCheckpointInfo() {
    // given
    state.setLatestCheckpointInfo(5L, 10L);
    state.setLatestBackupInfo(7L, 14L);

    // when
    state.setLatestBackupInfo(21L, 28L);

    // then
    assertThat(state.getLatestCheckpointId()).isEqualTo(5L);
    assertThat(state.getLatestCheckpointPosition()).isEqualTo(10L);
    assertThat(state.getLatestBackupId()).isEqualTo(21L);
    assertThat(state.getLatestBackupPosition()).isEqualTo(28L);
  }
}
