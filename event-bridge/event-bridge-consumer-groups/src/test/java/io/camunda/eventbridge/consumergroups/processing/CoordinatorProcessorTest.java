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
import io.camunda.eventbridge.consumergroups.state.appliers.MemberTakenOverApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.OffsetCommittedApplier;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.offset.DbOffsetState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
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
import java.time.Duration;
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
  private static final Duration DEBOUNCE = Duration.ofSeconds(2);

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbConsumerGroupState state;
  private DbOffsetState offsetState;
  private RecordProcessingEngine engine;
  private CoordinationValidator validator;

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

    validator = new CoordinationValidator(state, TOPIC_REGISTRY);
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
                        new JoinGroupProcessor(processors.writers(), state, validator, DEBOUNCE))
                    .onCommand(
                        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                        CoordinatorIntent.LEAVE_GROUP,
                        new LeaveGroupProcessor(processors.writers(), state, validator, DEBOUNCE))
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
                        CoordinatorIntent.MEMBER_TAKEN_OVER, new MemberTakenOverApplier(state))
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
  void shouldStampPreparingRebalanceAndDebounceTheRebalanceOnJoin() {
    // when — a join at t=0 with a 2s debounce
    join("g", "m1");

    // then — the join resolves to PREPARING_REBALANCE with the rebalance due one debounce later: in
    // the due index at its deadline, but not before it
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(1);
    assertThat(state.getGroup("g").getRebalanceDueAt()).isEqualTo(DEBOUNCE.toMillis());
    assertThat(groupIds(state.rebalancesDueBy(DEBOUNCE.toMillis() - 1))).isEmpty();
    assertThat(groupIds(state.rebalancesDueBy(DEBOUNCE.toMillis()))).containsExactly("g");
    assertThat(groupIds(state.emptyGroups())).isEmpty();
  }

  @Test
  void shouldKeepTheRebalanceDeadlineWhileAlreadyPending() {
    // given — a join opens the debounce window at t=0 → due at 2000
    join("g", "m1", 0L);

    // when — a second join lands while still pending (t=1000)
    join("g", "m2", 1000L);

    // then — the deadline is kept (a fixed window from the first change), not pushed to 3000
    assertThat(state.getGroup("g").getRebalanceDueAt()).isEqualTo(DEBOUNCE.toMillis());
    assertThat(groupIds(state.rebalancesDueBy(DEBOUNCE.toMillis()))).containsExactly("g");
  }

  @Test
  void shouldNotMoveTheRebalanceDeadlineAcrossAChurnBurst() {
    // given — preamble regression (#24 item 1): RebalanceDebounce.dueAt is arm-if-absent, so a
    // burst of membership churn within the window must still resolve to ONE fixed deadline
    // measured from the first change, not a sliding one each change could keep postponing.

    // when — join, join, leave, join, all within the 2s window opened at t=0
    join("g", "m1", 0L);
    join("g", "m2", 500L);
    leave("g", "m2", 2, 1000L);
    join("g", "m3", 1500L);

    // then — the due-ordered index still fires at firstChange (0) + debounce, unmoved by the burst
    assertThat(state.getGroup("g").getRebalanceDueAt()).isEqualTo(DEBOUNCE.toMillis());
    assertThat(groupIds(state.rebalancesDueBy(DEBOUNCE.toMillis() - 1))).isEmpty();
    assertThat(groupIds(state.rebalancesDueBy(DEBOUNCE.toMillis()))).containsExactly("g");
  }

  // --- static-membership takeover (task #24 item 2) ----------------------------------------------

  @Test
  void shouldTakeOverALiveStaticInstanceIdWithStrictlyHigherEpochAndVerbatimAssignment() {
    // given — a static member joins and receives a target assignment
    join("g", "m1", "inst-a", 0L);
    rebalance("g", 1, Map.of("m1", List.of(1, 2, 3, 4)));
    final var groupEpochBefore = state.getGroup("g").getGroupEpoch();
    final var assignmentEpochBefore = state.getGroup("g").getAssignmentEpoch();
    final var rebalanceDueAtBefore = state.getGroup("g").getRebalanceDueAt();
    final var lifecycleBefore = state.getGroup("g").getState();
    final var oldEpoch = state.getMember("g", "m1").getMemberEpoch();

    // when — a fresh incarnation (a restart) joins with the SAME instance id
    join("g", "restart-request-id", "inst-a", 1000L);

    // then — the SAME memberId "m1" is kept (no new roster entry), its epoch strictly increased,
    // its target is inherited verbatim, and the group is completely untouched (no rebalance: same
    // groupEpoch/assignmentEpoch/rebalanceDueAt/lifecycle)
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m1");
    assertThat(state.getMember("g", "m1").getMemberEpoch()).isGreaterThan(oldEpoch);
    assertThat(state.getMember("g", "m1").getTargetPartitions())
        .containsExactly(tp(1), tp(2), tp(3), tp(4));
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(groupEpochBefore);
    assertThat(state.getGroup("g").getAssignmentEpoch()).isEqualTo(assignmentEpochBefore);
    assertThat(state.getGroup("g").getRebalanceDueAt()).isEqualTo(rebalanceDueAtBefore);
    assertThat(state.getGroup("g").getState()).isEqualTo(lifecycleBefore);
  }

  @Test
  void shouldFenceTheSupersededIncarnationsCommandsAfterTakeover() {
    // given — m1 joins and is then taken over (its epoch strictly increases)
    join("g", "m1", "inst-a", 0L);
    final var oldEpoch = state.getMember("g", "m1").getMemberEpoch();
    join("g", "restart-request-id", "inst-a", 1000L);
    final var newEpoch = state.getMember("g", "m1").getMemberEpoch();
    assertThat(newEpoch).isGreaterThan(oldEpoch);

    // then — a heartbeat presenting the superseded epoch is fenced. There is no heartbeat command
    // on this replicated stream (heartbeats are served off-actor by HeartbeatHandler, whose
    // validateEpoch is byte-for-byte the same expected-vs-presented check CoordinationValidator
    // applies here); exercising it via validateLeave — the closest command this harness can drive —
    // proves the SAME memberId row (reused, not replaced) now reports FENCED_MEMBER_EPOCH for the
    // old epoch instead of UNKNOWN_MEMBER_ID, with no change needed in HeartbeatHandler itself.
    assertThat(
            validator
                .validateLeave(
                    new MembershipRecord()
                        .setGroupId("g")
                        .setMemberId("m1")
                        .setMemberEpoch(oldEpoch))
                .getLeft()
                .code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);

    // and — an offset commit presenting the superseded epoch is fenced identically
    assertThat(
            validator
                .validateCommit(
                    new OffsetCommitRecord()
                        .setGroupId("g")
                        .setMemberId("m1")
                        .setMemberEpoch(oldEpoch)
                        .setTopic("t")
                        .setPartitionId(1)
                        .setOffset(5))
                .getLeft()
                .code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);

    // while the NEW epoch is accepted
    assertThat(
            validator
                .validateLeave(
                    new MembershipRecord()
                        .setGroupId("g")
                        .setMemberId("m1")
                        .setMemberEpoch(newEpoch))
                .isRight())
        .isTrue();
  }

  @Test
  void shouldStrictlyIncreaseEpochAcrossTwoSuccessiveTakeoversAndFenceBothPriorIncarnations() {
    // given — m1 joins, then is taken over twice in a row (double restart)
    join("g", "m1", "inst-a", 0L);
    final var epoch0 = state.getMember("g", "m1").getMemberEpoch();

    join("g", "restart-1", "inst-a", 1000L);
    final var epoch1 = state.getMember("g", "m1").getMemberEpoch();
    assertThat(epoch1).isGreaterThan(epoch0);

    join("g", "restart-2", "inst-a", 2000L);
    final var epoch2 = state.getMember("g", "m1").getMemberEpoch();
    assertThat(epoch2).isGreaterThan(epoch1);

    // then — the roster still holds exactly one member, and BOTH prior incarnations are fenced
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m1");
    assertThat(
            validator
                .validateLeave(
                    new MembershipRecord().setGroupId("g").setMemberId("m1").setMemberEpoch(epoch0))
                .getLeft()
                .code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);
    assertThat(
            validator
                .validateLeave(
                    new MembershipRecord().setGroupId("g").setMemberId("m1").setMemberEpoch(epoch1))
                .getLeft()
                .code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);
  }

  @Test
  void shouldInheritThePendingRebalanceStateAndNotRearmTheDebounceOnTakeoverWhilePreparing() {
    // given — m1 joins (opens the debounce window at t=0), then a second (dynamic) member joins
    // while it is still pending, keeping the group PREPARING_REBALANCE
    join("g", "m1", "inst-a", 0L);
    join("g", "m2", 500L);
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    final var dueAtBefore = state.getGroup("g").getRebalanceDueAt();

    // when — m1 is taken over while the group is still PREPARING_REBALANCE
    join("g", "restart-request-id", "inst-a", 1000L);

    // then — the pending state and its deadline are inherited unmoved, not re-armed by the takeover
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.getGroup("g").getRebalanceDueAt()).isEqualTo(dueAtBefore);
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m1", "m2");
  }

  @Test
  void shouldInheritAnEmptyAssignmentOnTakeoverOfANeverAssignedMember() {
    // given — m1 joins but is never rebalanced (owns nothing)
    join("g", "m1", "inst-a", 0L);
    assertThat(state.getMember("g", "m1").getTargetPartitions()).isEmpty();

    // when — it is taken over before ever receiving a target
    join("g", "restart-request-id", "inst-a", 500L);

    // then — the inherited assignment is still exactly empty, and no rebalance was triggered
    assertThat(state.getMember("g", "m1").getTargetPartitions()).isEmpty();
    assertThat(state.getMember("g", "m1").getMemberEpoch()).isEqualTo(2);
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(1);
  }

  @Test
  void shouldTreatAJoinForAReleasedInstanceIdAsANormalJoinNotATakeover() {
    // given — m1 holds "inst-a", then leaves (the group empties, releasing the instance id)
    join("g", "m1", "inst-a", 0L);
    leave("g", "m1", 1, 500L);
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.EMPTY);

    // when — a new member joins with the SAME (now-released) instance id
    join("g", "m2", "inst-a", 1000L);

    // then — this is a NORMAL join: a fresh member "m2" (not "m1"), with the usual epoch bump and
    // rebalance — the boundary between takeover and fresh join
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m2");
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(3);
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.getMember("g", "m2").getMemberEpoch()).isEqualTo(3);
  }

  @Test
  void shouldTreatADynamicJoinAsANormalJoinRegardlessOfAnyExistingStaticMember() {
    // given — a static member already holds an instance id
    join("g", "m1", "inst-a", 0L);

    // when — a dynamic member (no instance id) joins the same group
    join("g", "m2", 500L);

    // then — unaffected by the static member: a fresh member, epoch bump + rebalance, no takeover
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m1", "m2");
    assertThat(state.getMember("g", "m2").getMemberEpoch()).isEqualTo(2);
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(2);
  }

  @Test
  void shouldStampEmptyAndEmptySinceWhenLastMemberLeaves() {
    join("g", "m1");

    // when — the only member leaves at a known time
    leave("g", "m1", 1, 1_700_000_000_000L);

    // then — the group is retained EMPTY with the stamped deadline, in the retention index and out
    // of the rebalance-due index
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.EMPTY);
    assertThat(state.getGroup("g").getEmptySince()).isEqualTo(1_700_000_000_000L);
    assertThat(groupIds(state.emptyGroups())).containsExactly("g");
    assertThat(groupIds(state.rebalancesDueBy(Long.MAX_VALUE))).isEmpty();
  }

  @Test
  void shouldStampPreparingRebalanceWhenAMemberRemains() {
    join("g", "m1");
    join("g", "m2");

    // when — one of two members leaves
    leave("g", "m1", 1, 0L);

    // then — the group stays alive in PREPARING_REBALANCE (no emptySince), still in the
    // rebalance-due index
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.getGroup("g").getEmptySince()).isZero();
    assertThat(groupIds(state.rebalancesDueBy(Long.MAX_VALUE))).containsExactly("g");
    assertThat(groupIds(state.emptyGroups())).isEmpty();
  }

  @Test
  void shouldStampReconcilingOnRebalanceAndLeaveTheDueIndex() {
    join("g", "m1");
    join("g", "m2");

    // when — the assignor's target is committed (group epoch is 2 after two joins)
    rebalance("g", 2, Map.of("m1", List.of(1, 2), "m2", List.of(3, 4)));

    // then — RECONCILING, targets applied, deadline cleared and out of both indexes
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.RECONCILING);
    assertThat(state.getGroup("g").getRebalanceDueAt()).isZero();
    assertThat(state.getMember("g", "m1").getTargetPartitions()).containsExactly(tp(1), tp(2));
    assertThat(groupIds(state.rebalancesDueBy(Long.MAX_VALUE))).isEmpty();
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
    join(group, member, 0L);
  }

  private void join(final String group, final String member, final long timestamp) {
    process(
        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
        CoordinatorIntent.JOIN_GROUP,
        new MembershipRecord().setGroupId(group).setMemberId(member).setTopics(List.of("t")),
        timestamp);
  }

  /**
   * A static-member join carrying {@code instanceId}. {@code requestedMemberId} is the id the
   * coordinator's membership actor would have freshly minted before this command ever reaches the
   * processor (see {@code ConsumerGroupCoordinator#handleJoinGroup}); a takeover discards it and
   * keeps the incumbent's memberId instead, so passing an obviously-throwaway value for it on a
   * takeover-triggering call documents that the processor never uses it in that case.
   */
  private void join(
      final String group,
      final String requestedMemberId,
      final String instanceId,
      final long timestamp) {
    process(
        EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
        CoordinatorIntent.JOIN_GROUP,
        new MembershipRecord()
            .setGroupId(group)
            .setMemberId(requestedMemberId)
            .setInstanceId(instanceId)
            .setTopics(List.of("t")),
        timestamp);
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
