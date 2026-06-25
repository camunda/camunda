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
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
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
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the stateless assignor: it proposes a target for every group whose replicated rebalance
 * deadline has passed (read from the due-ordered index against its clock) and for nothing before
 * that, with no in-memory debounce of its own.
 */
final class RebalanceAssignorTaskTest {

  private static final long DUE_AT = 5_000L;

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbConsumerGroupState state;
  private MemberJoinedApplier memberJoined;
  private GroupRebalancedApplier groupRebalanced;
  private MutableClock clock;
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
    clock = new MutableClock();
    task =
        new RebalanceAssignorTask(
            Duration.ofSeconds(1), state, new BalancedStickyAssignor(), clock);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldProposeTargetOnceTheRebalanceIsDue() {
    // given — a group whose rebalance is due at DUE_AT
    join("m1", 1, 1);

    // when — before the deadline, nothing is proposed
    clock.setMillis(DUE_AT - 1);
    assertThat(run()).isEmpty();

    // then — at the deadline the target is proposed
    clock.setMillis(DUE_AT);
    final var proposals = run();
    assertThat(proposals).hasSize(1);
    assertThat(proposals.get(0).getGroupId()).isEqualTo("g");
    assertThat(proposals.get(0).getAssignmentEpoch()).isEqualTo(1);
    assertThat(proposals.get(0).getMembers().get("m1"))
        .containsExactlyInAnyOrder(tp(1), tp(2), tp(3), tp(4));
  }

  @Test
  void shouldProposeForTheLatestRoster() {
    // given — two members joined, both due at DUE_AT (the second kept the first's deadline)
    join("m1", 1, 1);
    join("m2", 2, 2);

    // then — once due, the proposal covers the whole roster
    clock.setMillis(DUE_AT);
    final var proposals = run();
    assertThat(proposals).hasSize(1);
    assertThat(proposals.get(0).getAssignmentEpoch()).isEqualTo(2);
    assertThat(proposals.get(0).getMembers()).containsOnlyKeys("m1", "m2");
  }

  @Test
  void shouldNotProposeOnceTheTargetIsApplied() {
    // given — a due group whose target has already been committed (RECONCILING)
    join("m1", 1, 1);
    groupRebalanced.applyState(
        10,
        new RebalanceRecord()
            .setGroupId("g")
            .setAssignmentEpoch(1)
            .setMembers(Map.of("m1", List.of(tp(1), tp(2), tp(3), tp(4))))
            .setState(GroupLifecycle.RECONCILING));

    // then — it has left the due index, so nothing is proposed even past the deadline
    clock.setMillis(DUE_AT);
    assertThat(run()).isEmpty();
  }

  private void join(final String memberId, final long memberEpoch, final long groupEpoch) {
    memberJoined.applyState(
        memberEpoch,
        new MembershipRecord()
            .setGroupId("g")
            .setSubscriptions(Map.of("t", 4))
            .setMemberId(memberId)
            .setMemberEpoch(memberEpoch)
            .setGroupEpoch(groupEpoch)
            .setState(GroupLifecycle.PREPARING_REBALANCE)
            .setRebalanceDueAt(DUE_AT));
  }

  private static TopicPartition tp(final int partition) {
    return new TopicPartition("t", partition);
  }

  private List<RebalanceRecord> run() {
    final var builder = new CapturingTaskResultBuilder();
    task.execute(builder);
    return builder.proposals;
  }

  private static final class MutableClock implements InstantSource {
    private Instant now = Instant.ofEpochMilli(0);

    private void setMillis(final long millis) {
      now = Instant.ofEpochMilli(millis);
    }

    @Override
    public Instant instant() {
      return now;
    }
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
