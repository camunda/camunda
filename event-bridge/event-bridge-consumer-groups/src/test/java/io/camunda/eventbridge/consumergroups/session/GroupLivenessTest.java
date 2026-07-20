/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.consumergroups.session.GroupLiveness.MemberLiveness;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot.MemberSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The stalled-rebalance eviction rules: a heartbeating member that never confirms the target is
 * evicted, a never-heartbeated roster member only after a full session timeout from when the sweep
 * first observed it. Evicting the latter immediately was the rejoin-storm wedge: with a stale
 * rebalance on the books, every freshly joined member was evicted before its first heartbeat could
 * land, each eviction re-arming the rebalance it was blamed for — the group could never rebuild.
 */
final class GroupLivenessTest {

  private static final Instant NOW = Instant.ofEpochMilli(1_700_000_000_000L);
  private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration REBALANCE_TIMEOUT = Duration.ofSeconds(10);
  private static final Instant STALLED_SINCE = NOW.minus(Duration.ofMinutes(5));

  @Test
  void shouldGraceAFreshlyJoinedMemberDuringAStalledRebalance() {
    // given a long-stalled rebalance and a roster member that has never heartbeated on this leader
    final var liveness = new GroupLiveness(STALLED_SINCE, Map.of());
    final var group = preparing("m1");

    // when the sweep observes the member for the first time now
    final var evicted =
        liveness.membersToEvict(group, NOW, SESSION_TIMEOUT, REBALANCE_TIMEOUT, memberId -> NOW);

    // then it is NOT evicted — a fresh joiner must get the chance to heartbeat (the wedge:
    // evicting it here made every joiner a corpse before its first heartbeat)
    assertThat(evicted).isEmpty();
  }

  @Test
  void shouldEvictANeverHeartbeatedMemberAfterAFullSessionTimeout() {
    // given the same stalled rebalance, with the silent member first observed a session ago
    final var liveness = new GroupLiveness(STALLED_SINCE, Map.of());
    final var group = preparing("m1");
    final var firstSeen = NOW.minus(SESSION_TIMEOUT).minusSeconds(1);

    // when the sweep runs now
    final var evicted =
        liveness.membersToEvict(
            group, NOW, SESSION_TIMEOUT, REBALANCE_TIMEOUT, memberId -> firstSeen);

    // then the true corpse — joined, died, never spoke — is evicted
    assertThat(evicted).containsExactly("m1");
  }

  @Test
  void shouldStillEvictAHeartbeatingMemberThatNeverConfirmsAStalledTarget() {
    // given a committed target (epoch 2) stalled past the rebalance timeout, and a member that
    // keeps heartbeating but never confirms it
    final var liveness =
        new GroupLiveness(STALLED_SINCE, Map.of("m1", new MemberLiveness(NOW, 1L)));
    final var group = reconciling("m1");

    // when the sweep runs
    final var evicted =
        liveness.membersToEvict(group, NOW, SESSION_TIMEOUT, REBALANCE_TIMEOUT, memberId -> null);

    // then the live-but-stuck member is evicted so the group can converge without it
    assertThat(evicted).containsExactly("m1");
  }

  @Test
  void shouldNotStallEvictBeforeTheRebalanceTimeout() {
    // given a rebalance that only just started, and a never-heartbeated roster member first
    // observed long ago
    final var liveness = new GroupLiveness(NOW.minusSeconds(1), Map.of());
    final var group = preparing("m1");
    final var firstSeen = NOW.minus(Duration.ofMinutes(10));

    // when the sweep runs
    final var evicted =
        liveness.membersToEvict(
            group, NOW, SESSION_TIMEOUT, REBALANCE_TIMEOUT, memberId -> firstSeen);

    // then nothing is stall-evicted — the rebalance is still within its window
    assertThat(evicted).isEmpty();
  }

  /** A group awaiting its target: groupEpoch ahead of assignmentEpoch. */
  private static GroupSnapshot preparing(final String... memberIds) {
    return group(2L, 1L, GroupLifecycle.PREPARING_REBALANCE, memberIds);
  }

  /** A group whose target (epoch 2) is committed but not yet confirmed by every member. */
  private static GroupSnapshot reconciling(final String... memberIds) {
    return group(2L, 2L, GroupLifecycle.RECONCILING, memberIds);
  }

  private static GroupSnapshot group(
      final long groupEpoch,
      final long assignmentEpoch,
      final GroupLifecycle state,
      final String... memberIds) {
    final Map<String, MemberSnapshot> roster = new LinkedHashMap<>();
    for (final var memberId : memberIds) {
      roster.put(
          memberId, new MemberSnapshot(memberId, null, groupEpoch, 1L, List.of(), List.of()));
    }
    return new GroupSnapshot(
        "g", groupEpoch, assignmentEpoch, state, 0L, Map.of("t", 4), 0, roster);
  }
}
