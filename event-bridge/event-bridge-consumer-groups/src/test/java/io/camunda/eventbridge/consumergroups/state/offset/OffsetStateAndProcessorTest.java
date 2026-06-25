/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.offset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.OffsetCommittedApplier;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the coordinator's replicated offset state and that replay dispatches to its applier. */
final class OffsetStateAndProcessorTest {

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbOffsetState state;
  private OffsetCommittedApplier applier;

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
    applier = new OffsetCommittedApplier(state);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldStoreCommittedOffsetFromEvent() {
    // The applier writes the event's value verbatim — the monotonic never-rewind guard is resolved
    // by the OffsetCommitProcessor and stamped on the event (covered in CoordinatorProcessorTest).
    applier.applyState(1, commit("group-a", 1, 5));
    assertThat(state.getOffset("group-a", "t", 1)).isEqualTo(5);

    applier.applyState(2, commit("group-a", 1, 8));
    assertThat(state.getOffset("group-a", "t", 1)).isEqualTo(8);
  }

  @Test
  void shouldReturnMinusOneForUnknownOffset() {
    assertThat(state.getOffset("group-a", "t", 99)).isEqualTo(-1);
  }

  @Test
  void shouldIsolateOffsetsAcrossGroups() {
    applier.applyState(1, commit("group-a", 1, 5));
    applier.applyState(2, commit("group-b", 1, 7));

    assertThat(state.getOffset("group-a", "t", 1)).isEqualTo(5);
    assertThat(state.getOffset("group-b", "t", 1)).isEqualTo(7);
  }

  @Test
  void shouldSnapshotAllOffsetsForGroupFromState() {
    applier.applyState(1, commit("group-a", 1, 8));
    applier.applyState(2, commit("group-a", 2, 4));
    applier.applyState(3, commit("group-b", 1, 99));

    // the query service reads committed offsets from state (its own context), no mirror — only
    // group-a's partitions, sorted
    final var query = new OffsetQueryService(db);
    assertThat(query.committedOffsets("group-a"))
        .containsExactly(
            Map.entry(new TopicPartition("t", 1), 8L), Map.entry(new TopicPartition("t", 2), 4L));
  }

  @Test
  void shouldReturnOnlyFilteredPartitionsWithMinusOneForUncommitted() {
    applier.applyState(1, commit("group-a", 1, 8));
    applier.applyState(2, commit("group-a", 2, 4));

    // given a filter for one committed and one uncommitted partition
    final var query = new OffsetQueryService(db);
    final var filter = List.of(new TopicPartition("t", 1), new TopicPartition("t", 9));

    // then only the requested partitions come back; the uncommitted one is -1
    assertThat(query.committedOffsets("group-a", filter))
        .containsExactly(
            Map.entry(new TopicPartition("t", 1), 8L), Map.entry(new TopicPartition("t", 9), -1L));
  }

  @Test
  void shouldReturnAllOffsetsWhenFilterIsEmpty() {
    applier.applyState(1, commit("group-a", 1, 8));
    applier.applyState(2, commit("group-a", 2, 4));

    final var query = new OffsetQueryService(db);
    assertThat(query.committedOffsets("group-a", List.of()))
        .isEqualTo(query.committedOffsets("group-a"));
  }

  @Test
  void shouldApplyCommittedEventOnReplay() {
    // given — a follower replaying an OFFSET_COMMITTED event through the engine's applier registry
    // (dispatch is by intent value, so this also guards the borrowed-ValueType intent mapping)
    final var engine =
        new RecordProcessingEngine(
            processors ->
                processors.withEventApplier(
                    CoordinatorIntent.OFFSET_COMMITTED, new OffsetCommittedApplier(state)));
    final var event =
        new OffsetCommitRecord()
            .setGroupId("group-a")
            .setTopic("t")
            .setPartitionId(3)
            .setOffset(42);
    final TypedRecord record = mock(TypedRecord.class);
    when(record.getValue()).thenReturn(event);
    when(record.getIntent()).thenReturn(CoordinatorIntent.OFFSET_COMMITTED);

    // when
    engine.replay(record);

    // then — follower state matches what a leader would have committed
    assertThat(state.getOffset("group-a", "t", 3)).isEqualTo(42);
  }

  private static OffsetCommitRecord commit(
      final String group, final int partition, final long offset) {
    return new OffsetCommitRecord()
        .setGroupId(group)
        .setTopic("t")
        .setPartitionId(partition)
        .setOffset(offset);
  }
}
