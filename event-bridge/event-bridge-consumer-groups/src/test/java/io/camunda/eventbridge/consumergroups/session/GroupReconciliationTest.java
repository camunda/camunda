/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot.MemberSnapshot;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies the ephemeral assign/revoke handshake the coordinator runs on each heartbeat. */
final class GroupReconciliationTest {

  private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");
  private final GroupReconciliation reconciliation = new GroupReconciliation();

  @Test
  void shouldAssignFullTargetToSingleMemberThenConfirm() {
    // given — a one-member group with a freshly computed target
    final var group = group(1, 1, member("m1", 1, 1, 2, 3, 4));

    // when — the member owns nothing yet
    final var first = reconciliation.reconcile(group, "m1", List.of(), NOW);

    // then — it is told to take its whole target and the group is still rebalancing
    assertThat(first.assign()).containsExactly(tp(1), tp(2), tp(3), tp(4));
    assertThat(first.revoke()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isTrue();

    // when — it reports owning the target
    final var second = reconciliation.reconcile(group, "m1", tps(1, 2, 3, 4), NOW);

    // then — nothing left to move and the group is stable
    assertThat(second.assign()).isEmpty();
    assertThat(second.revoke()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isFalse();
  }

  @Test
  void shouldWithholdPartitionsUntilPreviousOwnerRevokes() {
    // given — m1 has converged on the whole topic
    final var solo = group(1, 1, member("m1", 1, 1, 2, 3, 4));
    reconciliation.reconcile(solo, "m1", List.of(), NOW);
    reconciliation.reconcile(solo, "m1", tps(1, 2, 3, 4), NOW);

    // when — m2 joins and the target splits the topic
    final var split = group(2, 2, member("m1", 1, 1, 2), member("m2", 2, 3, 4));

    // m2 must not receive 3,4 yet — m1 still owns them
    final var m2First = reconciliation.reconcile(split, "m2", List.of(), NOW);
    assertThat(m2First.assign()).isEmpty();

    // m1 is told to revoke 3,4
    final var m1Revoke = reconciliation.reconcile(split, "m1", tps(1, 2, 3, 4), NOW);
    assertThat(m1Revoke.revoke()).containsExactly(tp(3), tp(4));
    assertThat(m1Revoke.assign()).isEmpty();

    // m1 confirms it dropped them
    reconciliation.reconcile(split, "m1", tps(1, 2), NOW);

    // then — m2 may now take 3,4
    final var m2Second = reconciliation.reconcile(split, "m2", List.of(), NOW);
    assertThat(m2Second.assign()).containsExactly(tp(3), tp(4));

    // and once m2 owns them the group is stable
    reconciliation.reconcile(split, "m2", tps(3, 4), NOW);
    assertThat(reconciliation.isRebalancing(split)).isFalse();
  }

  @Test
  void shouldRestoreSeededMemberToItsTargetAfterFailover() {
    // given — a member seeded as already-converged (as on leader activation)
    final var group = group(1, 1, member("m1", 1, 1, 2, 3, 4));
    reconciliation.seedSession(group.members().get("m1"), 1, NOW);

    // when — the re-attaching consumer reports owning nothing (its client restarted too)
    final var delta = reconciliation.reconcile(group, "m1", List.of(), NOW);

    // then — it is driven back to its confirmed target without a rebalance
    assertThat(delta.assign()).containsExactly(tp(1), tp(2), tp(3), tp(4));
    assertThat(delta.revoke()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isFalse();
  }

  @Test
  void shouldReportRebalancingWhileTargetIsPending() {
    // given — a member joined but the assignor has not computed a target yet
    final var group = group(1, 0, member("m1", 1));

    // when
    final var delta = reconciliation.reconcile(group, "m1", List.of(), NOW);

    // then — nothing to assign and the heartbeat signals a rebalance in progress
    assertThat(delta.assign()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isTrue();
  }

  @Test
  void shouldEvictMembersWhoseSessionLapsed() {
    // given — m1 heartbeated at NOW and is converged (so the group is not rebalancing)
    final var group = group(1, 1, member("m1", 1, 1, 2, 3, 4));
    reconciliation.reconcile(group, "m1", tps(1, 2, 3, 4), NOW);
    final var sessionTimeout = Duration.ofSeconds(5);
    final var rebalanceTimeout = Duration.ofMinutes(5);

    // then — not evicted while its last heartbeat is within the session timeout
    assertThat(
            reconciliation.liveness().membersToEvict(group, NOW, sessionTimeout, rebalanceTimeout))
        .isEmpty();
    // and evicted once its last heartbeat falls before the deadline (now - sessionTimeout)
    assertThat(
            reconciliation
                .liveness()
                .membersToEvict(
                    group, NOW.plus(Duration.ofSeconds(10)), sessionTimeout, rebalanceTimeout))
        .containsExactly("m1");
  }

  private static GroupSnapshot group(
      final long groupEpoch, final long assignmentEpoch, final MemberSnapshot... members) {
    final Map<String, MemberSnapshot> roster = new LinkedHashMap<>();
    for (final var member : members) {
      roster.put(member.memberId(), member);
    }
    final var state =
        assignmentEpoch < groupEpoch ? GroupLifecycle.PREPARING_REBALANCE : GroupLifecycle.STABLE;
    return new GroupSnapshot("g", groupEpoch, assignmentEpoch, state, Map.of("t", 4), roster);
  }

  private static MemberSnapshot member(
      final String memberId, final long memberEpoch, final int... target) {
    // assignedEpoch is irrelevant to the ephemeral handshake under test; mirror the member epoch.
    return new MemberSnapshot(memberId, null, memberEpoch, memberEpoch, tps(target));
  }

  private static TopicPartition tp(final int partition) {
    return new TopicPartition("t", partition);
  }

  private static List<TopicPartition> tps(final int... partitions) {
    return Arrays.stream(partitions).mapToObj(GroupReconciliationTest::tp).toList();
  }
}
