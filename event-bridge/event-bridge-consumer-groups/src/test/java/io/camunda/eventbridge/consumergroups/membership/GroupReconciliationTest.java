/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot.MemberSnapshot;
import java.time.Duration;
import java.time.Instant;
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
    final var group = group(1, 1, 4, member("m1", 1, List.of(1, 2, 3, 4)));

    // when — the member owns nothing yet
    final var first = reconciliation.reconcile(group, "m1", List.of(), NOW);

    // then — it is told to take its whole target and the group is still rebalancing
    assertThat(first.assign()).containsExactly(1, 2, 3, 4);
    assertThat(first.revoke()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isTrue();

    // when — it reports owning the target
    final var second = reconciliation.reconcile(group, "m1", List.of(1, 2, 3, 4), NOW);

    // then — nothing left to move and the group is stable
    assertThat(second.assign()).isEmpty();
    assertThat(second.revoke()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isFalse();
  }

  @Test
  void shouldWithholdPartitionsUntilPreviousOwnerRevokes() {
    // given — m1 has converged on the whole topic
    final var solo = group(1, 1, 4, member("m1", 1, List.of(1, 2, 3, 4)));
    reconciliation.reconcile(solo, "m1", List.of(), NOW);
    reconciliation.reconcile(solo, "m1", List.of(1, 2, 3, 4), NOW);

    // when — m2 joins and the target splits the topic
    final var split =
        group(2, 2, 4, member("m1", 1, List.of(1, 2)), member("m2", 2, List.of(3, 4)));

    // m2 must not receive 3,4 yet — m1 still owns them
    final var m2First = reconciliation.reconcile(split, "m2", List.of(), NOW);
    assertThat(m2First.assign()).isEmpty();

    // m1 is told to revoke 3,4
    final var m1Revoke = reconciliation.reconcile(split, "m1", List.of(1, 2, 3, 4), NOW);
    assertThat(m1Revoke.revoke()).containsExactly(3, 4);
    assertThat(m1Revoke.assign()).isEmpty();

    // m1 confirms it dropped them
    reconciliation.reconcile(split, "m1", List.of(1, 2), NOW);

    // then — m2 may now take 3,4
    final var m2Second = reconciliation.reconcile(split, "m2", List.of(), NOW);
    assertThat(m2Second.assign()).containsExactly(3, 4);

    // and once m2 owns them the group is stable
    reconciliation.reconcile(split, "m2", List.of(3, 4), NOW);
    assertThat(reconciliation.isRebalancing(split)).isFalse();
  }

  @Test
  void shouldRestoreSeededMemberToItsTargetAfterFailover() {
    // given — a member seeded as already-converged (as on leader activation)
    final var group = group(1, 1, 4, member("m1", 1, List.of(1, 2, 3, 4)));
    reconciliation.seedSession(group.members().get("m1"), 1, NOW);

    // when — the re-attaching consumer reports owning nothing (its client restarted too)
    final var delta = reconciliation.reconcile(group, "m1", List.of(), NOW);

    // then — it is driven back to its confirmed target without a rebalance
    assertThat(delta.assign()).containsExactly(1, 2, 3, 4);
    assertThat(delta.revoke()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isFalse();
  }

  @Test
  void shouldReportRebalancingWhileTargetIsPending() {
    // given — a member joined but the assignor has not computed a target yet
    final var group = group(1, 0, 4, member("m1", 1, List.of()));

    // when
    final var delta = reconciliation.reconcile(group, "m1", List.of(), NOW);

    // then — nothing to assign and the heartbeat signals a rebalance in progress
    assertThat(delta.assign()).isEmpty();
    assertThat(reconciliation.isRebalancing(group)).isTrue();
  }

  @Test
  void shouldExpireSessionsPastTheDeadline() {
    // given
    final var group = group(1, 1, 4, member("m1", 1, List.of(1, 2, 3, 4)));
    reconciliation.reconcile(group, "m1", List.of(1, 2, 3, 4), NOW);

    // then — a member is expired when its last heartbeat (NOW) is before the deadline cutoff
    assertThat(reconciliation.expiredMembers(NOW.minus(Duration.ofSeconds(5)))).isEmpty();
    assertThat(reconciliation.expiredMembers(NOW.plus(Duration.ofSeconds(5))))
        .containsExactly("m1");
  }

  private static GroupSnapshot group(
      final long groupEpoch,
      final long assignmentEpoch,
      final int partitionCount,
      final MemberSnapshot... members) {
    final Map<String, MemberSnapshot> roster = new LinkedHashMap<>();
    for (final var member : members) {
      roster.put(member.memberId(), member);
    }
    return new GroupSnapshot("g", groupEpoch, assignmentEpoch, partitionCount, roster);
  }

  private static MemberSnapshot member(
      final String memberId, final long memberEpoch, final List<Integer> target) {
    return new MemberSnapshot(memberId, null, memberEpoch, target);
  }
}
