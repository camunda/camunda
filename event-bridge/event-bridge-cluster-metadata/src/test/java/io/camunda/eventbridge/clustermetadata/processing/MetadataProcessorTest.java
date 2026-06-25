/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.clustermetadata.placement.SpreadPlacement;
import io.camunda.eventbridge.clustermetadata.record.MetadataRecordValues;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.appliers.PartitionLeaderReportedApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.TopicDeletedApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.TopicRegisteredApplier;
import io.camunda.eventbridge.clustermetadata.state.topic.DbTopicState;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the topic-admin command path through the {@link RecordProcessingEngine} (validate →
 * resolve → append → apply) and asserts on the resulting replicated registry, so it covers what the
 * processors decide and stamp: in particular the placement resolved server-side from the live
 * broker membership on create, and the target placement computed from the current partition count
 * on reassign. The appliers' verbatim writes are covered separately in {@code
 * TopicStateAndProcessorTest}.
 */
final class MetadataProcessorTest {

  private static final List<Integer> BROKERS = List.of(0, 1, 2);
  private static final SpreadPlacement PLACEMENT = new SpreadPlacement();

  @TempDir private Path dbDir;
  private ZeebeDb<MetadataColumnFamilies> db;
  private DbTopicState state;
  private RecordProcessingEngine engine;

  @BeforeEach
  void setUp() {
    final var factory =
        new ZeebeRocksDbFactory<MetadataColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
    db = factory.createDb(dbDir.toFile());
    state = new DbTopicState(db, db.createContext());

    final var validator = new TopicValidator(state);
    engine =
        new RecordProcessingEngine(
            processors ->
                processors
                    .onCommand(
                        MetadataRecordValues.TOPIC_VALUE_TYPE,
                        MetadataIntent.CREATE_TOPIC,
                        new CreateTopicProcessor(
                            processors.writers(), validator, PLACEMENT, () -> BROKERS))
                    .onCommand(
                        MetadataRecordValues.TOPIC_VALUE_TYPE,
                        MetadataIntent.REASSIGN_TOPIC,
                        new ReassignTopicProcessor(
                            processors.writers(), validator, state, PLACEMENT, () -> BROKERS))
                    .onCommand(
                        MetadataRecordValues.TOPIC_VALUE_TYPE,
                        MetadataIntent.DELETE_TOPIC,
                        new TopicDeleteProcessor(processors.writers(), validator))
                    .onCommand(
                        MetadataRecordValues.TOPIC_VALUE_TYPE,
                        MetadataIntent.REPORT_PARTITION_LEADER,
                        new ReportPartitionLeaderProcessor(processors.writers(), validator, state))
                    .withEventApplier(
                        MetadataIntent.TOPIC_REGISTERED, new TopicRegisteredApplier(state))
                    .withEventApplier(MetadataIntent.TOPIC_DELETED, new TopicDeletedApplier(state))
                    .withEventApplier(
                        MetadataIntent.PARTITION_LEADER_REPORTED,
                        new PartitionLeaderReportedApplier(state)));
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldResolvePlacementFromBrokersOnCreate() {
    // when — a valid create with no assignment in the command
    create("orders", 3, 2);

    // then — the processor resolved the placement from the live broker set and stamped CREATING
    final var meta = state.get("orders");
    assertThat(meta.status()).isEqualTo(TopicStatus.CREATING);
    assertThat(meta.partitionCount()).isEqualTo(3);
    assertThat(meta.replicationFactor()).isEqualTo(2);
    assertThat(meta.assignment()).isEqualTo(PLACEMENT.assign("orders", 3, 2, BROKERS));
  }

  @Test
  void shouldRejectCreateWithInvalidCounts() {
    // when — counts below 1 are rejected, so no event is applied
    create("orders", 0, 0);

    // then
    assertThat(state.get("orders")).isNull();
  }

  @Test
  void shouldRejectCreateOfExistingTopic() {
    create("orders", 3, 2);

    // when — re-creating the same topic is rejected; the original is untouched
    create("orders", 9, 1);

    // then
    assertThat(state.get("orders").partitionCount()).isEqualTo(3);
    assertThat(state.get("orders").replicationFactor()).isEqualTo(2);
  }

  @Test
  void shouldResolveTargetFromCurrentPartitionCountOnReassign() {
    create("orders", 3, 2);

    // when — reassign to replication factor 3 (carries only name + rf)
    reassign("orders", 3);

    // then — committed assignment is kept; the target is computed from the current partition count
    final var meta = state.get("orders");
    assertThat(meta.status()).isEqualTo(TopicStatus.CREATING);
    assertThat(meta.partitionCount()).isEqualTo(3);
    assertThat(meta.assignment()).isEqualTo(PLACEMENT.assign("orders", 3, 2, BROKERS));
    assertThat(meta.target()).isEqualTo(PLACEMENT.assign("orders", 3, 3, BROKERS));
  }

  @Test
  void shouldRejectReassignOfMissingTopic() {
    // when — reassigning a topic that does not exist is rejected
    reassign("missing", 3);

    // then
    assertThat(state.get("missing")).isNull();
  }

  @Test
  void shouldDeleteTopic() {
    create("orders", 3, 2);

    // when
    process(
        MetadataIntent.DELETE_TOPIC,
        new TopicRecord().setName("orders").setOp(TopicRecord.OP_DELETE));

    // then
    assertThat(state.get("orders")).isNull();
  }

  private void create(final String name, final int partitionCount, final int replicationFactor) {
    process(
        MetadataIntent.CREATE_TOPIC,
        new TopicRecord()
            .setName(name)
            .setOp(TopicRecord.OP_REGISTER)
            .setPartitionCount(partitionCount)
            .setReplicationFactor(replicationFactor));
  }

  @Test
  void shouldFlipToActiveOnceEveryPartitionHasAReportedLeader() {
    create("orders", 2, 1);

    // when — only partition 1 has reported a leader
    reportLeader("orders", 1, 0, 1);

    // then — still CREATING (partition 2 has no leader yet)
    assertThat(state.get("orders").status()).isEqualTo(TopicStatus.CREATING);
    assertThat(state.partitionsWithLeader("orders")).containsExactly(1);

    // when — partition 2 reports its leader, completing coverage
    reportLeader("orders", 2, 1, 1);

    // then — derived ACTIVE from replicated leadership
    assertThat(state.get("orders").status()).isEqualTo(TopicStatus.ACTIVE);
    assertThat(state.partitionsWithLeader("orders")).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void shouldRejectAStaleLeaderTerm() {
    create("orders", 1, 1);
    reportLeader("orders", 1, 0, 5);

    // when — a delayed report from a deposed leader (lower term) arrives
    reportLeader("orders", 1, 2, 3);

    // then — the stale report is rejected; the term-5 leader stands
    assertThat(state.leaderTerm("orders", 1)).isEqualTo(5);
  }

  private void reportLeader(
      final String name, final int partition, final int node, final long term) {
    process(
        MetadataIntent.REPORT_PARTITION_LEADER,
        new TopicRecord()
            .setName(name)
            .setPartitionId(partition)
            .setLeaderNode(node)
            .setLeaderTerm(term));
  }

  private void reassign(final String name, final int replicationFactor) {
    process(
        MetadataIntent.REASSIGN_TOPIC,
        new TopicRecord()
            .setName(name)
            .setOp(TopicRecord.OP_REGISTER)
            .setReplicationFactor(replicationFactor));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void process(final Intent intent, final UnifiedRecordValue value) {
    final TypedRecord record = mock(TypedRecord.class);
    when(record.getValueType()).thenReturn((ValueType) MetadataRecordValues.TOPIC_VALUE_TYPE);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValue()).thenReturn(value);
    when(record.getKey()).thenReturn(1L);
    when(record.getTimestamp()).thenReturn(0L);
    when(record.getRequestId()).thenReturn(1L);
    when(record.getRequestStreamId()).thenReturn(1);
    engine.process(record, mock(ProcessingResultBuilder.class));
  }
}
