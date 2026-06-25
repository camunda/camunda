/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberJoinedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberLeftApplier;
import io.camunda.eventbridge.consumergroups.state.group.ConsumerGroupQueryService;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the retention task reclaims an EMPTY group only after its retention window elapses. */
final class GroupRetentionTaskTest {

  private static final Duration RETENTION = Duration.ofMinutes(10);

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbConsumerGroupState state;
  private MemberJoinedApplier memberJoined;
  private MemberLeftApplier memberLeft;
  private MutableClock clock;
  private GroupRetentionTask task;

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
    memberLeft = new MemberLeftApplier(state);
    clock = new MutableClock(Instant.parse("2026-06-25T00:00:00Z"));
    task =
        new GroupRetentionTask(
            Duration.ofSeconds(10), RETENTION, new ConsumerGroupQueryService(db), clock);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldDeleteEmptyGroupOnlyAfterRetentionElapses() {
    // given — a group that has just become EMPTY
    memberJoined.applyState(1, join("g", "m1", 1));
    memberLeft.applyState(2, leave("g", "m1", 2));

    // when — first tick records when it became empty; not yet past retention
    assertThat(run()).isEmpty();

    // and — still within the retention window
    clock.advance(RETENTION.minusSeconds(1));
    assertThat(run()).isEmpty();

    // then — once retention elapses, a DELETE_GROUP is proposed
    clock.advance(Duration.ofSeconds(2));
    final var deletes = run();
    assertThat(deletes).hasSize(1);
    assertThat(deletes.get(0).getGroupId()).isEqualTo("g");
  }

  @Test
  void shouldNotDeleteAGroupThatStillHasMembers() {
    memberJoined.applyState(1, join("g", "m1", 1));

    clock.advance(RETENTION.plusMinutes(1));
    assertThat(run()).isEmpty();
  }

  private List<MembershipRecord> run() {
    final var builder = new CapturingTaskResultBuilder();
    task.execute(builder);
    return builder.deletes;
  }

  private static MembershipRecord join(final String group, final String member, final long epoch) {
    return new MembershipRecord()
        .setGroupId(group)
        .setSubscriptions(java.util.Map.of("t", 4))
        .setMemberId(member)
        .setMemberEpoch(epoch)
        .setGroupEpoch(epoch);
  }

  private static MembershipRecord leave(final String group, final String member, final long epoch) {
    return new MembershipRecord().setGroupId(group).setMemberId(member).setGroupEpoch(epoch);
  }

  private static final class MutableClock implements InstantSource {
    private Instant now;

    private MutableClock(final Instant now) {
      this.now = now;
    }

    private void advance(final Duration by) {
      now = now.plus(by);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** Captures the {@code DELETE_GROUP} commands the task appends. */
  private static final class CapturingTaskResultBuilder implements TaskResultBuilder {
    private final List<MembershipRecord> deletes = new ArrayList<>();

    @Override
    public boolean appendCommandRecord(
        final long key,
        final Intent intent,
        final UnifiedRecordValue value,
        final FollowUpCommandMetadata metadata) {
      assertThat(intent).isEqualTo(CoordinatorIntent.DELETE_GROUP);
      deletes.add((MembershipRecord) value);
      return true;
    }

    @Override
    public boolean canAppendRecords(
        final List<? extends UnifiedRecordValue> value, final FollowUpCommandMetadata metadata) {
      return true;
    }

    @Override
    public TaskResult build() {
      return () -> null;
    }
  }
}
