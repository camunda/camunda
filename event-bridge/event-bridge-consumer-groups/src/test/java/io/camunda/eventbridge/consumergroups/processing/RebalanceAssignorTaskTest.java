/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.consumergroups.assignor.BalancedStickyAssignor;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupRebalancedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberJoinedApplier;
import io.camunda.eventbridge.consumergroups.state.group.ConsumerGroupQueryService;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the async assignor's debounce and the target it proposes from the mirror. */
final class RebalanceAssignorTaskTest {

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbConsumerGroupState state;
  private MemberJoinedApplier memberJoined;
  private GroupRebalancedApplier groupRebalanced;
  private RebalanceAssignorTask task;

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
    memberJoined = new MemberJoinedApplier(state);
    groupRebalanced = new GroupRebalancedApplier(state);
    task =
        new RebalanceAssignorTask(
            java.time.Duration.ofSeconds(1),
            new ConsumerGroupQueryService(db),
            new BalancedStickyAssignor());
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldDebounceThenProposeTargetOncePerEpoch() {
    // given — a group with a pending rebalance (groupEpoch 1 > assignmentEpoch 0)
    join("m1", 1, 1);

    // when — first tick only observes the epoch (debounce)
    final var firstTick = run();
    assertThat(firstTick).isEmpty();

    // then — second tick (epoch stable for one interval) proposes the target
    final var secondTick = run();
    assertThat(secondTick).hasSize(1);
    final var proposal = secondTick.get(0);
    assertThat(proposal.getGroupId()).isEqualTo("g");
    assertThat(proposal.getAssignmentEpoch()).isEqualTo(1);
    assertThat(proposal.getMembers()).containsOnlyKeys("m1");
    assertThat(proposal.getMembers().get("m1"))
        .containsExactlyInAnyOrder(tp(1), tp(2), tp(3), tp(4));

    // and a third tick does not re-propose while the same epoch is still pending
    assertThat(run()).isEmpty();
  }

  @Test
  void shouldNotProposeWhenAssignmentIsUpToDate() {
    // given — the target is already applied (assignmentEpoch == groupEpoch)
    join("m1", 1, 1);
    groupRebalanced.applyState(
        10,
        new RebalanceRecord()
            .setGroupId("g")
            .setAssignmentEpoch(1)
            .setMembers(java.util.Map.of("m1", List.of(tp(1), tp(2), tp(3), tp(4)))));

    // then — no rebalance is proposed on any tick
    assertThat(run()).isEmpty();
    assertThat(run()).isEmpty();
  }

  @Test
  void shouldResetDebounceWhenRosterChanges() {
    // given — first member joins, observed once
    join("m1", 1, 1);
    assertThat(run()).isEmpty();

    // when — a second member joins (group epoch advances) before the proposal fires
    join("m2", 2, 2);

    // then — the changed epoch is only observed this tick (debounce restarts), no proposal yet
    assertThat(run()).isEmpty();
    // next stable tick proposes for the latest epoch across both members
    final var proposal = run();
    assertThat(proposal).hasSize(1);
    assertThat(proposal.get(0).getAssignmentEpoch()).isEqualTo(2);
    assertThat(proposal.get(0).getMembers()).containsOnlyKeys("m1", "m2");
  }

  private void join(final String memberId, final long memberEpoch, final long groupEpoch) {
    memberJoined.applyState(
        memberEpoch,
        new MembershipRecord()
            .setGroupId("g")
            .setSubscriptions(java.util.Map.of("t", 4))
            .setMemberId(memberId)
            .setMemberEpoch(memberEpoch)
            .setGroupEpoch(groupEpoch));
  }

  private static TopicPartition tp(final int partition) {
    return new TopicPartition("t", partition);
  }

  private List<RebalanceRecord> run() {
    final var builder = new CapturingTaskResultBuilder();
    task.execute(builder);
    return builder.proposals;
  }

  /** Captures the {@code REBALANCE_GROUP} commands the task appends. */
  private static final class CapturingTaskResultBuilder implements TaskResultBuilder {

    private final List<RebalanceRecord> proposals = new ArrayList<>();

    @Override
    public boolean appendCommandRecord(
        final long key,
        final Intent intent,
        final UnifiedRecordValue value,
        final FollowUpCommandMetadata metadata) {
      assertThat(intent).isEqualTo(CoordinatorIntent.REBALANCE_GROUP);
      proposals.add((RebalanceRecord) value);
      return true;
    }

    @Override
    public boolean canAppendRecords(
        final List<? extends UnifiedRecordValue> value, final FollowUpCommandMetadata metadata) {
      return true;
    }

    @Override
    public TaskResult build() {
      return () -> null; // the task's returned result is irrelevant to these assertions
    }
  }
}
