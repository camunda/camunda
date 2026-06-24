/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.session;

import io.camunda.eventbridge.consumergroups.assignor.PartitionAssignment.ReconciliationResult;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot.MemberSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The coordinator's <em>ephemeral</em> per-group reconciliation handshake, held in memory on the
 * leader's {@code ConsumerGroupCoordinator} actor. It drives each member from the partitions it
 * currently reports owning toward the group's durable target assignment (read from the replicated
 * {@link GroupSnapshot} mirror), using the incremental-cooperative protocol: a partition is not
 * assigned to its new owner until the previous owner confirms revoking it.
 *
 * <p>Only the durable membership/target/epochs are replicated; this handshake state is rebuilt from
 * heartbeats after a coordinator failover (sessions are seeded as already-converged to their
 * current target, so re-attaching consumers are not driven to revoke everything).
 */
public final class GroupReconciliation {

  private final Map<String, MemberSession> sessions = new ConcurrentHashMap<>();

  // Partitions that must move between members on the current target — withheld from their new owner
  // until the previous owner confirms revoking them.
  private final Set<Integer> pendingRevocations = new HashSet<>();

  // The assignment epoch this handshake has been set up for (0 = none observed yet).
  private long targetEpoch;
  private Instant rebalanceStartedAt;

  /**
   * Reconciles one heartbeat: detects a newly arrived target, refreshes liveness, and returns the
   * assign/revoke delta to send the member.
   */
  public ReconciliationResult reconcile(
      final GroupSnapshot group,
      final String memberId,
      final List<Integer> owned,
      final Instant now) {
    observeTarget(group, now);
    // Drop sessions for members no longer in the roster (left/evicted) so liveness stays bounded to
    // the current group; the member being reconciled is always in the roster (validated upstream).
    sessions.keySet().retainAll(group.members().keySet());

    final var member = group.members().get(memberId);
    final var session = sessionFor(memberId, now);
    session.touch(now);

    if (isStabilizing(group)) {
      return reconcileToTarget(session, member.targetPartitions(), owned);
    }
    // Steady state, or the target is still being computed: drive the member back to what it last
    // confirmed (a no-op once owned == confirmed; after a rejoin/failover re-attach it re-delivers
    // the confirmed partitions so the member recovers them instead of owning nothing).
    return restoreToConfirmed(session, owned);
  }

  /**
   * Whether the group is mid-rebalance (target computed, but not every member has confirmed it).
   */
  public boolean isRebalancing(final GroupSnapshot group) {
    return group.isRebalancePending() || isStabilizing(group);
  }

  /**
   * An immutable snapshot of this group's liveness for the {@link MemberLivenessMirror} — each
   * member's last heartbeat and confirmed epoch, plus the rebalance start. Published by the
   * heartbeat handler after each reconcile and read off-actor by the eviction task.
   */
  public GroupLiveness liveness() {
    final var members = new HashMap<String, GroupLiveness.MemberLiveness>();
    sessions.forEach(
        (memberId, session) ->
            members.put(
                memberId,
                new GroupLiveness.MemberLiveness(
                    session.lastHeartbeat(), session.confirmedEpoch())));
    return new GroupLiveness(rebalanceStartedAt, members);
  }

  /** Seeds a session as already-converged to its current target (used on leader activation). */
  public void seedSession(
      final MemberSnapshot member, final long assignmentEpoch, final Instant now) {
    sessions.put(
        member.memberId(),
        new MemberSession(member.memberId(), member.targetPartitions(), assignmentEpoch, now));
    targetEpoch = Math.max(targetEpoch, assignmentEpoch);
  }

  private void observeTarget(final GroupSnapshot group, final Instant now) {
    final var assignmentEpoch = group.assignmentEpoch();
    if (assignmentEpoch <= targetEpoch) {
      return;
    }
    // A new target arrived: recompute which partitions must be revoked before they can be
    // reassigned — those some member still confirms owning but no longer holds in the new target.
    pendingRevocations.clear();
    group
        .members()
        .forEach(
            (memberId, member) -> {
              final var session = sessions.get(memberId);
              if (session == null) {
                return;
              }
              final var targetSet = new HashSet<>(member.targetPartitions());
              session.confirmedAssignment().stream()
                  .filter(partition -> !targetSet.contains(partition))
                  .forEach(pendingRevocations::add);
            });
    targetEpoch = assignmentEpoch;
    rebalanceStartedAt = now;
  }

  private boolean isStabilizing(final GroupSnapshot group) {
    if (group.isRebalancePending()) {
      return false; // target not computed yet
    }
    for (final var memberId : group.members().keySet()) {
      final var session = sessions.get(memberId);
      if (session == null || session.confirmedEpoch() != group.assignmentEpoch()) {
        return true; // someone still has to confirm the current target
      }
    }
    return false;
  }

  /**
   * Lazily creates a session for a member seen at runtime (e.g. a freshly joined member's first
   * heartbeat). It starts owning nothing and having confirmed no target (epoch 0), so the
   * incremental handshake assigns it partitions only as their previous owners revoke them.
   * (Failover uses {@link #seedSession} instead, which treats a member as already at its target.)
   */
  private MemberSession sessionFor(final String memberId, final Instant now) {
    return sessions.computeIfAbsent(
        memberId, ignored -> new MemberSession(memberId, List.of(), 0L, now));
  }

  private ReconciliationResult reconcileToTarget(
      final MemberSession session, final List<Integer> target, final List<Integer> owned) {
    final var targetSet = new HashSet<>(target);
    final var ownedSet = new HashSet<>(owned);
    final var revoke = owned.stream().filter(p -> !targetSet.contains(p)).sorted().toList();
    final var assign = target.stream().filter(p -> !ownedSet.contains(p)).sorted().toList();

    // A partition the member previously confirmed but no longer reports is a completed revocation —
    // free it so its new owner may take it.
    session.confirmedAssignment().stream()
        .filter(p -> !ownedSet.contains(p))
        .forEach(pendingRevocations::remove);

    // Withhold assignments still pending revocation by their previous owner.
    final var safeAssign = assign.stream().filter(p -> !pendingRevocations.contains(p)).toList();

    if (assign.isEmpty() && revoke.isEmpty()) {
      session.confirm(targetEpoch, owned);
    }
    return new ReconciliationResult(revoke, safeAssign, target);
  }

  private ReconciliationResult restoreToConfirmed(
      final MemberSession session, final List<Integer> owned) {
    final var confirmed = session.confirmedAssignment();
    final var ownedSet = new HashSet<>(owned);
    final var assign = confirmed.stream().filter(p -> !ownedSet.contains(p)).sorted().toList();
    final var revoke = owned.stream().filter(p -> !confirmed.contains(p)).sorted().toList();
    return new ReconciliationResult(revoke, assign, List.copyOf(confirmed));
  }
}
