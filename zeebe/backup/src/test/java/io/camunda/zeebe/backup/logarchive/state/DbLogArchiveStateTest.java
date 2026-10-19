/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DbLogArchiveStateTest {

  @TempDir Path database;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private DbLogArchiveState state;

  @BeforeEach
  void setUp() {
    zeebeDb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE),
                SimpleMeterRegistry::new)
            .createDb(database.toFile());
    state = new DbLogArchiveState(zeebeDb, zeebeDb.createContext());
  }

  @AfterEach
  void tearDown() throws Exception {
    zeebeDb.close();
  }

  @Test
  void shouldFindPendingCheckpoint() {
    // given
    state.insertPendingCheckpoint(10L, 100L, CheckpointType.MANUAL_BACKUP, 5L);

    // when
    final var found = state.findCheckpoint(10L);

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getCheckpointPosition()).isEqualTo(100L);
    assertThat(found.get().getCheckpointTimestamp()).isEqualTo(5L);
  }

  @Test
  void shouldFindCheckpointCoveredByOpenObject() {
    // given
    state.insertOpenObject(
        new LogObject().setFirstIndex(1L),
        List.of(
            new CoveredCheckpoint()
                .setCheckpointId(20L)
                .setCheckpointPosition(200L)
                .setCheckpointType(CheckpointType.SCHEDULED_BACKUP)));

    // when
    final var found = state.findCheckpoint(20L);

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getCheckpointPosition()).isEqualTo(200L);
    assertThat(found.get().getCheckpointType()).isEqualTo(CheckpointType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldFindBoundaryCheckpointOfSealedChunkWithoutPosition() {
    // given
    state.insertChunk(
        new IndexChunk().setChunkNo(1L).setFirstCheckpointId(30L).setLastCheckpointId(32L));

    // then
    assertThat(state.findCheckpoint(30L)).isPresent();
    assertThat(state.findCheckpoint(32L)).isPresent();
    assertThat(state.findCheckpoint(30L).get().getCheckpointPosition()).isEqualTo(-1L);
    assertThat(state.findCheckpoint(31L)).isEmpty();
  }

  @Test
  void shouldNotFindUnknownCheckpoint() {
    assertThat(state.findCheckpoint(99L)).isEmpty();
  }

  @Test
  void shouldListPendingAndOpenObjectCheckpointsAfterIdInIdOrder() {
    // given
    state.insertPendingCheckpoint(12L, 120L, CheckpointType.MANUAL_BACKUP, 1L);
    state.insertPendingCheckpoint(5L, 50L, CheckpointType.MANUAL_BACKUP, 1L);
    state.insertOpenObject(
        new LogObject().setFirstIndex(1L),
        List.of(
            new CoveredCheckpoint().setCheckpointId(8L).setCheckpointPosition(80L),
            new CoveredCheckpoint().setCheckpointId(3L).setCheckpointPosition(30L)));

    // when
    final var checkpoints = state.checkpointsAfter(4L);

    // then
    assertThat(checkpoints)
        .extracting(CoveredCheckpoint::getCheckpointId)
        .containsExactly(5L, 8L, 12L);
  }
}
