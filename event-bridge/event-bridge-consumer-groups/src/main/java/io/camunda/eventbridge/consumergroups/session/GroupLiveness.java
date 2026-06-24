/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.session;

import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot of a group's <em>ephemeral</em> liveness — each member's last heartbeat and
 * the assignment epoch it has confirmed, plus when the in-flight rebalance started. The heartbeat
 * handler publishes one of these per group to the {@link MemberLivenessMirror} after every
 * reconcile; the off-actor session-eviction task reads them to decide who to evict. This mirrors
 * how {@link GroupSnapshot} exposes replicated state off-actor: an immutable record placed in a
 * concurrent map, so cross-actor reads need no locking.
 */
public record GroupLiveness(Instant rebalanceStartedAt, Map<String, MemberLiveness> members) {

  public GroupLiveness {
    members = Map.copyOf(members);
  }

  /** One member's liveness: when it last heartbeated and the assignment epoch it confirmed. */
  public record MemberLiveness(Instant lastHeartbeat, long confirmedEpoch) {}

  /**
   * The members to evict for this group on a tick: those in the roster whose session has lapsed (no
   * heartbeat within {@code sessionTimeout}), plus — when a rebalance has stalled past {@code
   * rebalanceTimeout} — those that never confirmed the current target. A roster member with no
   * liveness yet (freshly joined, not heartbeated) is left alone by the expiry check (a grace
   * period), but counts as non-converged for a stalled rebalance.
   */
  public List<String> membersToEvict(
      final GroupSnapshot group,
      final Instant now,
      final Duration sessionTimeout,
      final Duration rebalanceTimeout) {
    final var deadline = now.minus(sessionTimeout);
    final var evict = new LinkedHashSet<String>();

    members.forEach(
        (memberId, liveness) -> {
          if (group.members().containsKey(memberId)
              && liveness.lastHeartbeat().isBefore(deadline)) {
            evict.add(memberId);
          }
        });

    if (isRebalancing(group) && rebalanceStalled(now, rebalanceTimeout)) {
      group
          .members()
          .keySet()
          .forEach(
              memberId -> {
                final var liveness = members.get(memberId);
                if (liveness == null || liveness.confirmedEpoch() != group.assignmentEpoch()) {
                  evict.add(memberId);
                }
              });
    }
    return List.copyOf(evict);
  }

  private boolean isRebalancing(final GroupSnapshot group) {
    return group.isRebalancePending() || isStabilizing(group);
  }

  private boolean isStabilizing(final GroupSnapshot group) {
    if (group.isRebalancePending()) {
      return false; // target not computed yet
    }
    for (final var memberId : group.members().keySet()) {
      final var liveness = members.get(memberId);
      if (liveness == null || liveness.confirmedEpoch() != group.assignmentEpoch()) {
        return true; // someone still has to confirm the current target
      }
    }
    return false;
  }

  private boolean rebalanceStalled(final Instant now, final Duration timeout) {
    return rebalanceStartedAt != null
        && Duration.between(rebalanceStartedAt, now).compareTo(timeout) > 0;
  }
}
