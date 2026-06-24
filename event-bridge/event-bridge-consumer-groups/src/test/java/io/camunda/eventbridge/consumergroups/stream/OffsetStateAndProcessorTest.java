/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the coordinator's replicated offset state and the process/replay logic over RocksDB. */
final class OffsetStateAndProcessorTest {

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbOffsetState state;

  @BeforeEach
  void setUp() {
    final var factory =
        new ZeebeRocksDbFactory<EventBridgeColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
    db = factory.createDb(dbDir.toFile());
    state = new DbOffsetState(db, db.createContext());
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldCommitMonotonically() {
    // given / when / then — never moves backwards
    assertThat(state.commit("group-a", 1, 5)).isEqualTo(5);
    assertThat(state.commit("group-a", 1, 3)).isEqualTo(5);
    assertThat(state.commit("group-a", 1, 8)).isEqualTo(8);
    assertThat(state.getOffset("group-a", 1)).isEqualTo(8);
  }

  @Test
  void shouldReturnMinusOneForUnknownOffset() {
    assertThat(state.getOffset("group-a", 99)).isEqualTo(-1);
  }

  @Test
  void shouldIsolateOffsetsAcrossGroups() {
    // given
    state.commit("group-a", 1, 5);
    state.commit("group-b", 1, 7);

    // then
    assertThat(state.getOffset("group-a", 1)).isEqualTo(5);
    assertThat(state.getOffset("group-b", 1)).isEqualTo(7);
  }

  @Test
  void shouldReturnAllOffsetsForGroup() {
    // given
    state.commit("group-a", 1, 8);
    state.commit("group-a", 2, 4);
    state.commit("group-b", 1, 99);

    // then — only group-a's partitions, sorted
    assertThat(state.getOffsets("group-a"))
        .containsExactly(java.util.Map.entry(1, 8L), java.util.Map.entry(2, 4L));
  }

  @Test
  void shouldApplyCommittedEventOnReplay() {
    // given — a follower replaying an OFFSET_COMMITTED event through the engine's applier registry
    final var engine =
        RecordProcessingEngine.builder()
            .withEventApplier(CoordinatorIntent.OFFSET_COMMITTED, new OffsetCommittedApplier(state))
            .build();
    final var event =
        new OffsetCommitRecord().setGroupId("group-a").setPartitionId(3).setOffset(42);
    final TypedRecord record = mock(TypedRecord.class);
    when(record.getValue()).thenReturn(event);
    when(record.getIntent()).thenReturn(CoordinatorIntent.OFFSET_COMMITTED);

    // when
    engine.replay(record);

    // then — follower state matches what a leader would have committed
    assertThat(state.getOffset("group-a", 3)).isEqualTo(42);
  }
}
