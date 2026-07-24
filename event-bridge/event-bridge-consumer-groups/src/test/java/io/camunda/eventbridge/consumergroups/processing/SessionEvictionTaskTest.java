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
import io.camunda.eventbridge.consumergroups.session.GroupLiveness;
import io.camunda.eventbridge.consumergroups.session.GroupLiveness.MemberLiveness;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberJoinedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberTakenOverApplier;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
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
 * The session-eviction sweep's takeover-awareness (invariant 3 of the static-membership takeover
 * spec): a taken-over member reuses its predecessor's memberId, so the {@link MemberLivenessMirror}
 * entry the sweep finds for it is — until the successor's first heartbeat — still its
 * predecessor's, however stale. The sweep must not evict on that inherited staleness, while still
 * evicting a member whose liveness genuinely went stale under an unchanged epoch (the pre-existing
 * #23 behavior, unaffected by this).
 */
final class SessionEvictionTaskTest {

  private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REBALANCE_TIMEOUT = Duration.ofSeconds(30);

  @TempDir private Path dbDir;
  private ZeebeDb<EventBridgeColumnFamilies> db;
  private DbConsumerGroupState state;
  private MemberJoinedApplier memberJoined;
  private MemberTakenOverApplier memberTakenOver;
  private MemberLivenessMirror liveness;
  private MutableClock clock;
  private SessionEvictionTask task;

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
    memberTakenOver = new MemberTakenOverApplier(state);
    liveness = new MemberLivenessMirror();
    clock = new MutableClock(Instant.parse("2026-06-25T00:00:00Z"));
    task =
        new SessionEvictionTask(
            Duration.ofSeconds(1), SESSION_TIMEOUT, REBALANCE_TIMEOUT, state, liveness, clock);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldNotEvictAJustTakenOverMemberDespiteItsPredecessorsStaleLiveness() {
    // given — m1 joins and heartbeats normally (fresh liveness observed once, at epoch 1)
    memberJoined.applyState(1, join("g", "m1", 1));
    liveness.publish(
        "g", new GroupLiveness(null, Map.of("m1", new MemberLiveness(clock.instant(), 1L))));
    assertThat(run()).isEmpty();

    // when — a takeover applies (epoch 1 -> 2) but the successor has not heartbeated yet, so the
    // mirror still carries the PREDECESSOR's liveness; time then advances well past the session
    // timeout with no fresh heartbeat
    memberTakenOver.applyState(2, takenOver("g", "m1", 2));
    clock.advance(SESSION_TIMEOUT.plusSeconds(5));

    // then — m1 is not evicted: its predecessor's stale liveness must not be blamed on it
    assertThat(run()).isEmpty();
  }

  @Test
  void shouldStillEvictAMemberWhoseLivenessGenuinelyWentStaleUnderAnUnchangedEpoch() {
    // given — m1 joins and heartbeats normally (fresh liveness observed once, at epoch 1)
    memberJoined.applyState(1, join("g", "m1", 1));
    liveness.publish(
        "g", new GroupLiveness(null, Map.of("m1", new MemberLiveness(clock.instant(), 1L))));
    assertThat(run()).isEmpty();

    // when — NO takeover happens (epoch stays 1) and the member simply goes silent past the
    // session timeout
    clock.advance(SESSION_TIMEOUT.plusSeconds(5));

    // then — a genuinely silent member is still evicted (the pre-existing #23 behavior)
    assertThat(run()).containsExactly("m1");
  }

  @Test
  void
      shouldNotEvictAcrossMultipleTicksUntilTheSuccessorsFirstHeartbeatEvenPastTheSessionTimeout() {
    // given — m1 joins and heartbeats normally (fresh liveness observed once, at epoch 1)
    memberJoined.applyState(1, join("g", "m1", 1));
    liveness.publish(
        "g", new GroupLiveness(null, Map.of("m1", new MemberLiveness(clock.instant(), 1L))));
    assertThat(run()).isEmpty();

    // when — a takeover applies (epoch 1 -> 2); the mirror still carries the PREDECESSOR's stale
    // liveness (the successor has not heartbeated yet) across SEVERAL eviction ticks, well past
    // what would be the session timeout measured from the predecessor's last beat — the exact
    // window a single-tick strip would miss: the predecessor's lastHeartbeat never changes, so a
    // naive "strip only the tick the epoch-advance is detected" reintroduces the stale entry on
    // every subsequent tick
    memberTakenOver.applyState(2, takenOver("g", "m1", 2));
    clock.advance(Duration.ofSeconds(2));
    assertThat(run()).isEmpty(); // tick 1 after the takeover
    clock.advance(SESSION_TIMEOUT); // now well past predecessor-beat + sessionTimeout
    assertThat(run()).isEmpty(); // tick 2 — the bug window: must still not evict
    clock.advance(Duration.ofSeconds(5));
    assertThat(run()).isEmpty(); // tick 3 — still no heartbeat yet, still protected

    // then — the successor was never evicted for a heartbeat it never sent, across every tick of
    // the wait, not merely the tick the takeover was first observed on
  }

  @Test
  void shouldEndTheGraceOnceTheSuccessorHeartbeatsThenEvictOnOrdinaryStaleness() {
    // given — m1 joins and heartbeats normally, then is taken over (still no successor heartbeat)
    memberJoined.applyState(1, join("g", "m1", 1));
    liveness.publish(
        "g", new GroupLiveness(null, Map.of("m1", new MemberLiveness(clock.instant(), 1L))));
    assertThat(run()).isEmpty();
    memberTakenOver.applyState(2, takenOver("g", "m1", 2));
    clock.advance(Duration.ofSeconds(3));
    assertThat(run()).isEmpty(); // grace: stripped, not evicted

    // when — the successor's own first heartbeat lands (a fresh liveness entry at/after the
    // takeover was observed)
    clock.advance(Duration.ofSeconds(1));
    liveness.publish(
        "g", new GroupLiveness(null, Map.of("m1", new MemberLiveness(clock.instant(), 2L))));
    assertThat(run()).isEmpty(); // fresh heartbeat — not evicted, and the grace marker is cleared

    // then — grace does NOT persist forever: from here on ordinary session-timeout eviction
    // applies again, exactly as if this had never been a takeover
    clock.advance(SESSION_TIMEOUT.plusSeconds(1));
    assertThat(run()).containsExactly("m1");
  }

  private List<String> run() {
    final var builder = new CapturingTaskResultBuilder();
    task.execute(builder);
    return builder.evictedMemberIds;
  }

  private static MembershipRecord join(final String group, final String member, final long epoch) {
    return new MembershipRecord()
        .setGroupId(group)
        .setSubscriptions(Map.of("t", 4))
        .setMemberId(member)
        .setMemberEpoch(epoch)
        .setGroupEpoch(epoch)
        .setState(GroupLifecycle.STABLE);
  }

  private static MembershipRecord takenOver(
      final String group, final String member, final long memberEpoch) {
    return new MembershipRecord().setGroupId(group).setMemberId(member).setMemberEpoch(memberEpoch);
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

  /** Captures the memberIds of the {@code LEAVE_GROUP} commands the task appends. */
  private static final class CapturingTaskResultBuilder implements TaskResultBuilder {
    private final List<String> evictedMemberIds = new ArrayList<>();

    @Override
    public boolean appendCommandRecord(
        final long key,
        final Intent intent,
        final UnifiedRecordValue value,
        final FollowUpCommandMetadata metadata) {
      assertThat(intent).isEqualTo(CoordinatorIntent.LEAVE_GROUP);
      evictedMemberIds.add(((MembershipRecord) value).getMemberId());
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
