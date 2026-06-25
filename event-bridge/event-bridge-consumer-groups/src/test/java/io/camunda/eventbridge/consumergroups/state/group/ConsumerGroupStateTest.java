/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.consumergroups.processing.CoordinationValidator;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupDeletedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupRebalancedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberJoinedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberLeftApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.OffsetCommittedApplier;
import io.camunda.eventbridge.consumergroups.state.offset.DbOffsetState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
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
 * Verifies the consumer-group appliers (which own the create/update/delete decision logic), the
 * replicated state they write through (read back via {@link DbConsumerGroupState}, including its
 * {@code GroupSnapshot} reads), and the {@link CoordinationValidator} fencing.
 */
final class ConsumerGroupStateTest {

  // Stand-in topic registry: every topic has 4 partitions except "missing" (unknown/not servable).
  private static final TopicRegistry TOPIC_REGISTRY = topic -> "missing".equals(topic) ? 0 : 4;

  // The (debounced) rebalance deadline the processor would stamp; applier tests stamp it directly.
  private static final long REBALANCE_DUE = 1_000L;

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbConsumerGroupState state;
  private DbOffsetState offsetState;

  private MemberJoinedApplier memberJoined;
  private MemberLeftApplier memberLeft;
  private GroupRebalancedApplier groupRebalanced;
  private OffsetCommittedApplier offsetCommitted;

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
    memberJoined = new MemberJoinedApplier(state);
    memberLeft = new MemberLeftApplier(state);
    groupRebalanced = new GroupRebalancedApplier(state);
    offsetCommitted = new OffsetCommittedApplier(offsetState);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldAddMemberAndCreateGroupOnJoin() {
    // when
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));

    // then — durable state + snapshot agree, group created with the carried partition count
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(1);
    assertThat(state.getGroup("g").getAssignmentEpoch()).isZero();

    final var snapshot = state.groupSnapshot("g");
    assertThat(snapshot.subscriptions()).containsExactly(Map.entry("t", 4));
    // first join enters the state machine at PREPARING_REBALANCE (target not yet computed)
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(snapshot.state()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(snapshot.members()).containsOnlyKeys("m1");
    assertThat(snapshot.members().get("m1").memberEpoch()).isEqualTo(1);
    assertThat(snapshot.members().get("m1").targetPartitions()).isEmpty();
    assertThat(snapshot.isRebalancePending()).isTrue();
    // the group is in the due-ordered rebalance index at its deadline, but not before it
    assertThat(state.getGroup("g").getRebalanceDueAt()).isEqualTo(REBALANCE_DUE);
    assertThat(groupIds(state.rebalancesDueBy(REBALANCE_DUE - 1))).isEmpty();
    assertThat(groupIds(state.rebalancesDueBy(REBALANCE_DUE))).containsExactly("g");
  }

  @Test
  void shouldKeepPartitionCountAndCarryGroupEpochAcrossJoins() {
    // when — a second member joins (the event carries the bumped group epoch)
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));
    memberJoined.applyState(2, join("g", "m2", null, 2, 2, 4));

    // then
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(2);
    assertThat(state.groupSnapshot("g").subscriptions()).containsEntry("t", 4);
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m1", "m2");
  }

  @Test
  void shouldStartJoiningMemberOwningNothing() {
    // when — a member joins (the assignor has not run yet)
    memberJoined.applyState(1, join("g", "m1", "instance-a", 1, 1, 4));

    // then — it owns no partitions until a rebalance gives it a target
    assertThat(state.getMember("g", "m1").getTargetPartitions()).isEmpty();
  }

  @Test
  void shouldFenceDuplicateStaticInstanceIdOnJoin() {
    // given — a static member already holds an instance id
    memberJoined.applyState(1, join("g", "m1", "instance-a", 1, 1, 4));
    final var validator = new CoordinationValidator(state, TOPIC_REGISTRY);

    // then — a second join for that instance id is fenced (KIP-848 fences the new joiner)
    assertThat(validator.validateJoin(join("g", "m2", "instance-a", 0, 0, 4)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.UNRELEASED_INSTANCE_ID);
    // but a dynamic join (no instance id) and a join for a free instance id are allowed
    assertThat(validator.validateJoin(join("g", "m2", null, 0, 0, 4)).isRight()).isTrue();
    assertThat(validator.validateJoin(join("g", "m2", "instance-b", 0, 0, 4)).isRight()).isTrue();
  }

  @Test
  void shouldFindStaticMemberByInstanceId() {
    memberJoined.applyState(1, join("g", "m1", "instance-a", 1, 1, 4));
    memberJoined.applyState(2, join("g", "m2", null, 2, 2, 4));

    assertThat(state.findMemberByInstanceId("g", "instance-a")).isEqualTo("m1");
    assertThat(state.findMemberByInstanceId("g", "unknown")).isNull();
    assertThat(state.findMemberByInstanceId("g", null)).isNull();
  }

  @Test
  void shouldApplyTargetAssignmentAndAdvanceAssignmentEpoch() {
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));
    memberJoined.applyState(2, join("g", "m2", null, 2, 2, 4));

    groupRebalanced.applyState(
        3, rebalance("g", 2, Map.of("m1", List.of(1, 2), "m2", List.of(3, 4))));

    assertThat(state.getGroup("g").getAssignmentEpoch()).isEqualTo(2);
    assertThat(state.groupSnapshot("g").isRebalancePending()).isFalse();
    // a committed rebalance transitions the group to RECONCILING (members not yet converged)
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.RECONCILING);
    assertThat(state.getMember("g", "m1").getTargetPartitions()).containsExactly(tp(1), tp(2));
    assertThat(state.groupSnapshot("g").members().get("m2").targetPartitions())
        .containsExactly(tp(3), tp(4));
  }

  @Test
  void shouldRemoveMemberAndBumpGroupEpochOnLeave() {
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));
    memberJoined.applyState(2, join("g", "m2", null, 2, 2, 4));

    // a leave that keeps members resolves to PREPARING_REBALANCE (the LeaveGroupProcessor's job),
    // keeping the group in the rebalance-due index
    memberLeft.applyState(
        3,
        leave("g", "m1", 3)
            .setState(GroupLifecycle.PREPARING_REBALANCE)
            .setRebalanceDueAt(REBALANCE_DUE));

    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(3);
    assertThat(state.getMember("g", "m1")).isNull();
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m2");
    assertThat(groupIds(state.rebalancesDueBy(REBALANCE_DUE))).containsExactly("g");
  }

  @Test
  void shouldRetainGroupAsEmptyWhenLastMemberLeaves() {
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));
    offsetCommitted.applyState(2, commit("g", 1, 9));

    memberLeft.applyState(
        3, leave("g", "m1", 2).setState(GroupLifecycle.EMPTY).setEmptySince(1_700_000_000_000L));

    // the group is retained as EMPTY (not deleted) and its committed offsets survive
    assertThat(state.getGroup("g")).isNotNull();
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.EMPTY);
    assertThat(state.groupSnapshot("g").members()).isEmpty();
    assertThat(offsetState.getOffset("g", "t", 1)).isEqualTo(9);
    // emptySince is written from the event (the retention deadline base), and the group is in the
    // EMPTY index (the retention task's work list) and out of the rebalance-due index
    assertThat(state.getGroup("g").getEmptySince()).isEqualTo(1_700_000_000_000L);
    assertThat(state.getGroup("g").getRebalanceDueAt()).isZero();
    assertThat(groupIds(state.emptyGroups())).containsExactly("g");
    assertThat(groupIds(state.rebalancesDueBy(REBALANCE_DUE))).isEmpty();
  }

  @Test
  void shouldDeleteEmptyGroupAndItsOffsetsOnGroupDeleted() {
    // given — an EMPTY group with a committed offset
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));
    offsetCommitted.applyState(2, commit("g", 1, 9));
    memberLeft.applyState(3, leave("g", "m1", 2).setState(GroupLifecycle.EMPTY));
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.EMPTY);

    // when — the retention path deletes it
    new GroupDeletedApplier(state, offsetState)
        .applyState(4, new MembershipRecord().setGroupId("g"));

    // then — the group, its offsets, and its index entry are gone
    assertThat(state.getGroup("g")).isNull();
    assertThat(offsetState.getOffset("g", "t", 1)).isEqualTo(-1);
    assertThat(groupIds(state.emptyGroups())).isEmpty();
  }

  @Test
  void shouldReviveEmptyGroupToPreparingRebalanceOnRejoin() {
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));
    memberLeft.applyState(
        2, leave("g", "m1", 2).setState(GroupLifecycle.EMPTY).setEmptySince(1_700_000_000_000L));
    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.EMPTY);
    assertThat(state.getGroup("g").getEmptySince()).isEqualTo(1_700_000_000_000L);

    // a new member joining the retained group revives it
    memberJoined.applyState(3, join("g", "m2", null, 3, 3, 4));

    assertThat(state.getGroup("g").getState()).isEqualTo(GroupLifecycle.PREPARING_REBALANCE);
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m2");
    // reviving clears the retention deadline and moves the group from the EMPTY to the
    // rebalance-due index
    assertThat(state.getGroup("g").getEmptySince()).isZero();
    assertThat(groupIds(state.emptyGroups())).isEmpty();
    assertThat(groupIds(state.rebalancesDueBy(REBALANCE_DUE))).containsExactly("g");
  }

  @Test
  void shouldReconstructSnapshotFromDurableStateOnAFreshContext() {
    memberJoined.applyState(1, join("g", "m1", "instance-a", 1, 1, 4));
    groupRebalanced.applyState(2, rebalance("g", 1, Map.of("m1", List.of(1, 2, 3, 4))));

    // a state view built on its own context (as a new leader's reader would) reads the durable
    // state directly — there is no mirror to seed.
    final var recovered = new DbConsumerGroupState(db, db.createContext());

    final var snapshot = recovered.groupSnapshot("g");
    assertThat(snapshot.groupEpoch()).isEqualTo(1);
    assertThat(snapshot.assignmentEpoch()).isEqualTo(1);
    assertThat(snapshot.members().get("m1").instanceId()).isEqualTo("instance-a");
    assertThat(snapshot.members().get("m1").targetPartitions())
        .containsExactly(tp(1), tp(2), tp(3), tp(4));
  }

  @Test
  void shouldCommitOffsetsMonotonically() {
    offsetCommitted.applyState(1, commit("g", 1, 5));
    offsetCommitted.applyState(2, commit("g", 1, 3)); // older — ignored
    offsetCommitted.applyState(3, commit("g", 1, 8));

    assertThat(offsetState.getOffset("g", "t", 1)).isEqualTo(8);
  }

  @Test
  void shouldFenceOffsetCommitsAgainstMembership() {
    memberJoined.applyState(1, join("g", "m1", null, 5, 5, 4));
    groupRebalanced.applyState(2, rebalance("g", 5, Map.of("m1", List.of(1, 2))));
    final var validator = new CoordinationValidator(state, TOPIC_REGISTRY);

    assertThat(validator.validateCommit(commit("g", "m1", 5, 1)).isRight()).isTrue();
    assertThat(validator.validateCommit(commit("g", "ghost", 5, 1)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.UNKNOWN_MEMBER_ID);
    assertThat(validator.validateCommit(commit("g", "m1", 4, 1)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);
    assertThat(validator.validateCommit(commit("g", "m1", 5, 3)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.NOT_PARTITION_OWNER);
  }

  @Test
  void shouldValidateLeaveAndJoinAgainstMembership() {
    memberJoined.applyState(1, join("g", "m1", null, 2, 2, 4));
    final var validator = new CoordinationValidator(state, TOPIC_REGISTRY);

    assertThat(validator.validateLeave(leave("g", "m1", 2)).isRight()).isTrue();
    assertThat(validator.validateLeave(leave("g", "m1", 1)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);
    assertThat(validator.validateLeave(leave("g", "ghost", 2)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.UNKNOWN_MEMBER_ID);
    assertThat(validator.validateJoin(join("", "m1", null, 0, 0, 0)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.INVALID_GROUP_ID);
  }

  @Test
  void shouldRejectJoinWithMissingTopic() {
    final var validator = new CoordinationValidator(state, TOPIC_REGISTRY);

    // when — a join carries no subscribed topic
    final var rejection = validator.validateJoin(join("g", "", "m1", null, 0, 0, 4));

    // then
    assertThat(rejection.getLeft().code()).isEqualTo(CoordinationErrorCode.INVALID_TOPIC);
  }

  @Test
  void shouldRejectJoinForUnservableTopic() {
    final var validator = new CoordinationValidator(state, TOPIC_REGISTRY);

    // when — the coordinator could not resolve a partition count (unknown/not servable topic)
    final var rejection = validator.validateJoin(join("g", "missing", "m1", null, 0, 0, 0));

    // then
    assertThat(rejection.getLeft().code()).isEqualTo(CoordinationErrorCode.TOPIC_NOT_FOUND);
  }

  @Test
  void shouldRejectJoinToGroupBoundToDifferentTopic() {
    // given — a group already bound to topic "t"
    memberJoined.applyState(1, join("g", "t", "m1", null, 1, 1, 4));
    final var validator = new CoordinationValidator(state, TOPIC_REGISTRY);

    // when — a second member joins the same group subscribing to a different topic
    final var rejection = validator.validateJoin(join("g", "other", "m2", null, 0, 0, 4));

    // then
    assertThat(rejection.getLeft().code()).isEqualTo(CoordinationErrorCode.INVALID_TOPIC);
    // but a join with the bound topic is accepted
    assertThat(validator.validateJoin(join("g", "t", "m2", null, 0, 0, 4)).isRight()).isTrue();
  }

  private static MembershipRecord join(
      final String group,
      final String member,
      final String instanceId,
      final long memberEpoch,
      final long groupEpoch,
      final int partitionCount) {
    return join(group, "t", member, instanceId, memberEpoch, groupEpoch, partitionCount);
  }

  private static MembershipRecord join(
      final String group,
      final String topic,
      final String member,
      final String instanceId,
      final long memberEpoch,
      final long groupEpoch,
      final int partitionCount) {
    // The resolved MEMBER_JOINED event the JoinGroupProcessor would produce (a join always lands in
    // PREPARING_REBALANCE with the retention deadline cleared); the applier writes it verbatim.
    return new MembershipRecord()
        .setGroupId(group)
        .setSubscriptions(Map.of(topic, partitionCount))
        .setMemberId(member)
        .setInstanceId(instanceId)
        .setMemberEpoch(memberEpoch)
        .setGroupEpoch(groupEpoch)
        .setState(GroupLifecycle.PREPARING_REBALANCE)
        .setEmptySince(0L)
        .setRebalanceDueAt(REBALANCE_DUE);
  }

  private static MembershipRecord leave(final String group, final String member, final long epoch) {
    // For applier tests the carried group epoch is what matters; for validation tests the member
    // epoch is — set both to the same value so each test reads the field it cares about. Applier
    // tests stamp the resolved state/emptySince (the LeaveGroupProcessor's job) per case.
    return new MembershipRecord()
        .setGroupId(group)
        .setMemberId(member)
        .setMemberEpoch(epoch)
        .setGroupEpoch(epoch);
  }

  private static RebalanceRecord rebalance(
      final String group, final long assignmentEpoch, final Map<String, List<Integer>> targets) {
    // Targets are given as partition ints of the single topic "t" for brevity; a committed
    // rebalance
    // resolves to RECONCILING (the RebalanceProcessor's job), written verbatim by the applier.
    final Map<String, List<TopicPartition>> tpTargets = new LinkedHashMap<>();
    targets.forEach(
        (member, partitions) ->
            tpTargets.put(member, partitions.stream().map(p -> tp(p)).toList()));
    return new RebalanceRecord()
        .setGroupId(group)
        .setAssignmentEpoch(assignmentEpoch)
        .setMembers(tpTargets)
        .setState(GroupLifecycle.RECONCILING);
  }

  private static OffsetCommitRecord commit(
      final String group, final String member, final long epoch, final int partition) {
    return new OffsetCommitRecord()
        .setGroupId(group)
        .setTopic("t")
        .setMemberId(member)
        .setMemberEpoch(epoch)
        .setPartitionId(partition)
        .setOffset(1);
  }

  private static OffsetCommitRecord commit(
      final String group, final int partition, final long offset) {
    return new OffsetCommitRecord()
        .setGroupId(group)
        .setTopic("t")
        .setPartitionId(partition)
        .setOffset(offset);
  }

  private static TopicPartition tp(final int partition) {
    return new TopicPartition("t", partition);
  }

  private static List<String> groupIds(final List<GroupSnapshot> snapshots) {
    return snapshots.stream().map(GroupSnapshot::groupId).toList();
  }
}
