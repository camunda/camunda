/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.consumergroups.record.EventBridgeRecordValues;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupDeletedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupRebalancedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberJoinedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberLeftApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberReconciledApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.OffsetCommittedApplier;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.offset.DbOffsetState;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the full command path through the {@link RecordProcessingEngine} (validate → resolve →
 * append → apply) and asserts on the resulting replicated state, so it covers what the processors
 * decide and stamp: the lifecycle transitions, the emptySince deadline, the monotonic offset, and
 * the lifecycle-index upkeep — plus the rejections the {@link CoordinationValidator} / {@link
 * TransitionValidator} produce. The appliers' verbatim writes are covered separately in {@code
 * ConsumerGroupStateTest}.
 */
final class CoordinatorProcessorTest {

  private static final TopicRegistry TOPIC_REGISTRY = topic -> "missing".equals(topic) ? 0 : 4;

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbConsumerGroupState state;
  private DbOffsetState offsetState;
  private RecordProcessingEngine engine;

  @BeforeEach
  void setUp() {
    final var factory =
        new ZeebeRocksDbFactory<EventBridgeColumnFamilies>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);
    db = factory.createDb(dbDir.toFile());
    state = new DbConsumerGroupState(db, db.createContext());
    offsetState = new DbOffsetState(db, db.createContext());

    final var validator = new CoordinationValidator(state, TOPIC_REGISTRY);
    final var transitions = new TransitionValidator(state);
    engine =
        new RecordProcessingEngine(
            processors ->
                processors
                    .onCommand(
                        EventBridgeRecordValues.OFFSET_VALUE_TYPE,
                        CoordinatorIntent.COMMIT_OFFSET,
                        new OffsetCommitProcessor(processors.writers(), offsetState, validator))
                    .onCommand(
                        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                        CoordinatorIntent.JOIN_GROUP,
                        new JoinGroupProcessor(processors.writers(), state, validator))
                    .onCommand(
                        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                        CoordinatorIntent.LEAVE_GROUP,
                        new LeaveGroupProcessor(processors.writers(), state, validator))
                    .onCommand(
                        EventBridgeRecordValues.REBALANCE_VALUE_TYPE,
                        CoordinatorIntent.REBALANCE_GROUP,
                        new RebalanceProcessor(processors.writers(), transitions))
                    .onCommand(
                        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                        CoordinatorIntent.RECONCILE_MEMBER,
                        new ReconcileMemberProcessor(processors.writers(), state, transitions))
                    .onCommand(
                        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                        CoordinatorIntent.DELETE_GROUP,
                        new DeleteGroupProcessor(processors.writers(), transitions))
                    .withEventApplier(
                        CoordinatorIntent.OFFSET_COMMITTED, new OffsetCommittedApplier(offsetState))
                    .withEventApplier(
                        CoordinatorIntent.MEMBER_JOINED, new MemberJoinedApplier(state))
                    .withEventApplier(CoordinatorIntent.MEMBER_LEFT, new MemberLeftApplier(state))
                    .withEventApplier(
                        CoordinatorIntent.MEMBER_RECONCILED, new MemberReconciledApplier(state))
                    .withEventApplier(
                        CoordinatorIntent.GROUP_REBALANCED, new GroupRebalancedApplier(state))
                    .withEventApplier(
                        CoordinatorIntent.GROUP_DELETED,
                        new GroupDeletedApplier(state, offsetState)));
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldStampPreparingRebalanceAndTrackPendingOnJoin() {
    // when
    join("g", "m1");

    // then — the join resolves to PREPARING_REBALANCE and lands in the assignor's pending index
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(1);
    assertThat(groupIds(state.pendingRebalanceGroups())).containsExactly("g");
    assertThat(groupIds(state.emptyGroups())).isEmpty();
  }

  @Test
  void shouldStampEmptyAndEmptySinceWhenLastMemberLeaves() {
    join("g", "m1");

    // when — the only member leaves at a known time
    leave("g", "m1", 1, 1_700_000_000_000L);

    // then — the group is retained EMPTY with the stamped deadline, in the retention index
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.EMPTY);
    assertThat(state.getGroup("g").getEmptySince()).isEqualTo(1_700_000_000_000L);
    assertThat(groupIds(state.emptyGroups())).containsExactly("g");
    assertThat(groupIds(state.pendingRebalanceGroups())).isEmpty();
  }

  @Test
  void shouldStampPreparingRebalanceWhenAMemberRemains() {
    join("g", "m1");
    join("g", "m2");

    // when — one of two members leaves
    leave("g", "m1", 1, 0L);

    // then — the group stays alive in PREPARING_REBALANCE (no emptySince), back in the pending
    // index
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.getGroup("g").getEmptySince()).isZero();
    assertThat(groupIds(state.pendingRebalanceGroups())).containsExactly("g");
    assertThat(groupIds(state.emptyGroups())).isEmpty();
  }

  @Test
  void shouldStampReconcilingOnRebalanceAndLeavePendingIndex() {
    join("g", "m1");
    join("g", "m2");

    // when — the assignor's target is committed (group epoch is 2 after two joins)
    rebalance("g", 2, Map.of("m1", List.of(1, 2), "m2", List.of(3, 4)));

    // then — RECONCILING, targets applied, and out of the pending index (neither index)
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.RECONCILING);
    assertThat(state.getMember("g", "m1").getTargetPartitions()).containsExactly(tp(1), tp(2));
    assertThat(groupIds(state.pendingRebalanceGroups())).isEmpty();
    assertThat(groupIds(state.emptyGroups())).isEmpty();
  }

  @Test
  void shouldStampStableOnlyWhenEveryMemberHasReconciled() {
    join("g", "m1");
    join("g", "m2");
    rebalance("g", 2, Map.of("m1", List.of(1, 2), "m2", List.of(3, 4)));

    // when — only m1 reconciles to the group epoch
    reconcile("g", "m1", 2);

    // then — still RECONCILING (m2 lags)
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.RECONCILING);
    assertThat(state.getMember("g", "m1").getAssignedEpoch()).isEqualTo(2);

    // when — m2 reconciles too
    reconcile("g", "m2", 2);

    // then — every member is at the group epoch, so the group is STABLE
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.STABLE);
  }

  @Test
  void shouldResolveOffsetMonotonicallyInTheProcessor() {
    join("g", "m1");
    rebalance("g", 1, Map.of("m1", List.of(1, 2, 3, 4)));

    // when — a commit, then a lower commit, then a higher one
    commit("g", "m1", 1, 1, 5);
    assertThat(offsetState.getOffset("g", "t", 1)).isEqualTo(5);

    commit("g", "m1", 1, 1, 3); // older — must not move the offset backwards
    assertThat(offsetState.getOffset("g", "t", 1)).isEqualTo(5);

    commit("g", "m1", 1, 1, 8);
    assertThat(offsetState.getOffset("g", "t", 1)).isEqualTo(8);
  }

  @Test
  void shouldRejectReconcileForUnknownMember() {
    join("g", "m1");

    // when — a reconcile for a member that does not exist (TransitionValidator rejects it)
    reconcile("g", "ghost", 1);

    // then — no state change: m1 is untouched and the group stays PREPARING_REBALANCE
    assertThat(state.getMember("g", "m1").getAssignedEpoch()).isZero();
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
  }

  @Test
  void shouldDropDeleteWhenGroupIsNoLongerEmpty() {
    join("g", "m1");
    leave("g", "m1", 1, 0L); // group is now EMPTY
    join("g", "m2"); // ... but a member rejoined before the delete is processed

    // when — the retention task's DELETE_GROUP is processed after the revive
    delete("g");

    // then — the validator drops it: the group still exists, back in PREPARING_REBALANCE
    assertThat(state.getGroup("g")).isNotNull();
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m2");
  }

  // --- command helpers --------------------------------------------------------------------------

  private void join(final String group, final String member) {
    process(
        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
        CoordinatorIntent.JOIN_GROUP,
        new MembershipRecord().setGroupId(group).setMemberId(member).setTopics(List.of("t")),
        0L);
  }

  private void leave(
      final String group, final String member, final long memberEpoch, final long timestamp) {
    process(
        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
        CoordinatorIntent.LEAVE_GROUP,
        new MembershipRecord().setGroupId(group).setMemberId(member).setMemberEpoch(memberEpoch),
        timestamp);
  }

  private void rebalance(
      final String group, final long assignmentEpoch, final Map<String, List<Integer>> targets) {
    final Map<String, List<TopicPartition>> tpTargets = new LinkedHashMap<>();
    targets.forEach(
        (member, partitions) -> tpTargets.put(member, partitions.stream().map(this::tp).toList()));
    process(
        EventBridgeRecordValues.REBALANCE_VALUE_TYPE,
        CoordinatorIntent.REBALANCE_GROUP,
        new RebalanceRecord()
            .setGroupId(group)
            .setAssignmentEpoch(assignmentEpoch)
            .setMembers(tpTargets),
        0L);
  }

  private void reconcile(final String group, final String member, final long groupEpoch) {
    process(
        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
        CoordinatorIntent.RECONCILE_MEMBER,
        new MembershipRecord().setGroupId(group).setMemberId(member).setGroupEpoch(groupEpoch),
        0L);
  }

  private void commit(
      final String group,
      final String member,
      final long memberEpoch,
      final int partition,
      final long offset) {
    process(
        EventBridgeRecordValues.OFFSET_VALUE_TYPE,
        CoordinatorIntent.COMMIT_OFFSET,
        new OffsetCommitRecord()
            .setGroupId(group)
            .setTopic("t")
            .setMemberId(member)
            .setMemberEpoch(memberEpoch)
            .setPartitionId(partition)
            .setOffset(offset),
        0L);
  }

  private void delete(final String group) {
    process(
        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
        CoordinatorIntent.DELETE_GROUP,
        new MembershipRecord().setGroupId(group),
        0L);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void process(
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue value,
      final long timestamp) {
    final TypedRecord record = mock(TypedRecord.class);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValue()).thenReturn(value);
    when(record.getKey()).thenReturn(1L);
    when(record.getTimestamp()).thenReturn(timestamp);
    when(record.getRequestId()).thenReturn(1L);
    when(record.getRequestStreamId()).thenReturn(1);
    engine.process(record, mock(ProcessingResultBuilder.class));
  }

  private TopicPartition tp(final int partition) {
    return new TopicPartition("t", partition);
  }

  private static List<String> groupIds(final List<GroupSnapshot> snapshots) {
    return snapshots.stream().map(GroupSnapshot::groupId).toList();
  }
}
