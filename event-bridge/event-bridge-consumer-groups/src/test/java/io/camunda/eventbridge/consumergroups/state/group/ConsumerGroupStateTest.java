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
import io.camunda.eventbridge.consumergroups.processing.CoordinationChecks;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
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
 * replicated state + mirror they write through, and the {@link CoordinationChecks} fencing.
 */
final class ConsumerGroupStateTest {

  // Stand-in topic registry: every topic has 4 partitions except "missing" (unknown/not servable).
  private static final TopicRegistry TOPIC_REGISTRY = topic -> "missing".equals(topic) ? 0 : 4;

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

    // then — durable state + mirror agree, group created with the carried partition count
    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(1);
    assertThat(state.getGroup("g").getAssignmentEpoch()).isZero();

    final var snapshot = state.groupSnapshot("g");
    assertThat(snapshot.subscriptions()).containsExactly(Map.entry("t", 4));
    assertThat(snapshot.members()).containsOnlyKeys("m1");
    assertThat(snapshot.members().get("m1").memberEpoch()).isEqualTo(1);
    assertThat(snapshot.members().get("m1").targetPartitions()).isEmpty();
    assertThat(snapshot.isRebalancePending()).isTrue();
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
    final var checks = new CoordinationChecks(state, TOPIC_REGISTRY);

    // then — a second join for that instance id is fenced (KIP-848 fences the new joiner)
    assertThat(checks.validateJoin(join("g", "m2", "instance-a", 0, 0, 4)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.UNRELEASED_INSTANCE_ID);
    // but a dynamic join (no instance id) and a join for a free instance id are allowed
    assertThat(checks.validateJoin(join("g", "m2", null, 0, 0, 4)).isRight()).isTrue();
    assertThat(checks.validateJoin(join("g", "m2", "instance-b", 0, 0, 4)).isRight()).isTrue();
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
    assertThat(state.getMember("g", "m1").getTargetPartitions()).containsExactly(tp(1), tp(2));
    assertThat(state.groupSnapshot("g").members().get("m2").targetPartitions())
        .containsExactly(tp(3), tp(4));
  }

  @Test
  void shouldRemoveMemberAndBumpGroupEpochOnLeave() {
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));
    memberJoined.applyState(2, join("g", "m2", null, 2, 2, 4));

    memberLeft.applyState(3, leave("g", "m1", 3));

    assertThat(state.getGroup("g").getGroupEpoch()).isEqualTo(3);
    assertThat(state.getMember("g", "m1")).isNull();
    assertThat(state.groupSnapshot("g").members()).containsOnlyKeys("m2");
  }

  @Test
  void shouldDeleteGroupWhenLastMemberLeaves() {
    memberJoined.applyState(1, join("g", "m1", null, 1, 1, 4));

    memberLeft.applyState(2, leave("g", "m1", 2));

    assertThat(state.getGroup("g")).isNull();
    assertThat(state.groupSnapshot("g")).isNull();
  }

  @Test
  void shouldRebuildMirrorFromDurableStateOnSeed() {
    memberJoined.applyState(1, join("g", "m1", "instance-a", 1, 1, 4));
    groupRebalanced.applyState(2, rebalance("g", 1, Map.of("m1", List.of(1, 2, 3, 4))));

    final var recovered = new DbConsumerGroupState(db, db.createContext());
    assertThat(recovered.groupSnapshot("g")).isNull(); // mirror empty until seeded

    recovered.seedMirror();

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
    final var checks = new CoordinationChecks(state, TOPIC_REGISTRY);

    assertThat(checks.validateCommit(commit("g", "m1", 5, 1)).isRight()).isTrue();
    assertThat(checks.validateCommit(commit("g", "ghost", 5, 1)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.UNKNOWN_MEMBER_ID);
    assertThat(checks.validateCommit(commit("g", "m1", 4, 1)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);
    assertThat(checks.validateCommit(commit("g", "m1", 5, 3)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.NOT_PARTITION_OWNER);
  }

  @Test
  void shouldValidateLeaveAndJoinAgainstMembership() {
    memberJoined.applyState(1, join("g", "m1", null, 2, 2, 4));
    final var checks = new CoordinationChecks(state, TOPIC_REGISTRY);

    assertThat(checks.validateLeave(leave("g", "m1", 2)).isRight()).isTrue();
    assertThat(checks.validateLeave(leave("g", "m1", 1)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.FENCED_MEMBER_EPOCH);
    assertThat(checks.validateLeave(leave("g", "ghost", 2)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.UNKNOWN_MEMBER_ID);
    assertThat(checks.validateJoin(join("", "m1", null, 0, 0, 0)).getLeft().code())
        .isEqualTo(CoordinationErrorCode.INVALID_GROUP_ID);
  }

  @Test
  void shouldRejectJoinWithMissingTopic() {
    final var checks = new CoordinationChecks(state, TOPIC_REGISTRY);

    // when — a join carries no subscribed topic
    final var rejection = checks.validateJoin(join("g", "", "m1", null, 0, 0, 4));

    // then
    assertThat(rejection.getLeft().code()).isEqualTo(CoordinationErrorCode.INVALID_TOPIC);
  }

  @Test
  void shouldRejectJoinForUnservableTopic() {
    final var checks = new CoordinationChecks(state, TOPIC_REGISTRY);

    // when — the coordinator could not resolve a partition count (unknown/not servable topic)
    final var rejection = checks.validateJoin(join("g", "missing", "m1", null, 0, 0, 0));

    // then
    assertThat(rejection.getLeft().code()).isEqualTo(CoordinationErrorCode.TOPIC_NOT_FOUND);
  }

  @Test
  void shouldRejectJoinToGroupBoundToDifferentTopic() {
    // given — a group already bound to topic "t"
    memberJoined.applyState(1, join("g", "t", "m1", null, 1, 1, 4));
    final var checks = new CoordinationChecks(state, TOPIC_REGISTRY);

    // when — a second member joins the same group subscribing to a different topic
    final var rejection = checks.validateJoin(join("g", "other", "m2", null, 0, 0, 4));

    // then
    assertThat(rejection.getLeft().code()).isEqualTo(CoordinationErrorCode.INVALID_TOPIC);
    // but a join with the bound topic is accepted
    assertThat(checks.validateJoin(join("g", "t", "m2", null, 0, 0, 4)).isRight()).isTrue();
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
    return new MembershipRecord()
        .setGroupId(group)
        .setSubscriptions(Map.of(topic, partitionCount))
        .setMemberId(member)
        .setInstanceId(instanceId)
        .setMemberEpoch(memberEpoch)
        .setGroupEpoch(groupEpoch);
  }

  private static MembershipRecord leave(final String group, final String member, final long epoch) {
    // For applier tests the carried group epoch is what matters; for validation tests the member
    // epoch is — set both to the same value so each test reads the field it cares about.
    return new MembershipRecord()
        .setGroupId(group)
        .setMemberId(member)
        .setMemberEpoch(epoch)
        .setGroupEpoch(epoch);
  }

  private static RebalanceRecord rebalance(
      final String group, final long assignmentEpoch, final Map<String, List<Integer>> targets) {
    // Targets are given as partition ints of the single topic "t" for brevity.
    final Map<String, List<TopicPartition>> tpTargets = new LinkedHashMap<>();
    targets.forEach(
        (member, partitions) ->
            tpTargets.put(member, partitions.stream().map(p -> tp(p)).toList()));
    return new RebalanceRecord()
        .setGroupId(group)
        .setAssignmentEpoch(assignmentEpoch)
        .setMembers(tpTargets);
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
}
