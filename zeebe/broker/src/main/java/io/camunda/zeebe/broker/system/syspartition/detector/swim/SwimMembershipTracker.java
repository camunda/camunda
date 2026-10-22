/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Tracks this broker's own view of the cluster membership as SWIM gossips it, and for how long each
 * broker has been continuously out of sight.
 *
 * <p>SWIM is unreliable by design: it suspects members that are merely slow, refutes suspicions
 * with a higher incarnation, and drops members for good after its failure timeout. The tracker only
 * records what SWIM says and never concludes a broker is gone itself: that is decided from many
 * views. What it does to be a useful witness:
 *
 * <ul>
 *   <li>a broker never seen alive stays {@link ObservedState#UNKNOWN}, so a broker that is merely
 *       starting is not reported as gone;
 *   <li>the time out of sight starts when the broker was last seen alive and survives the step from
 *       suspect to dead, so it measures the whole outage rather than SWIM's last phase;
 *   <li>a broker seen alive again is forgiven at once, so a refuted suspicion never adds up with a
 *       later one.
 * </ul>
 *
 * <p>Not thread-safe; used on the failure detector's actor.
 */
final class SwimMembershipTracker {
  private final String localMemberId;
  private final LongSupplier nanoClock;
  private final Map<String, Entry> entries = new HashMap<>();

  /**
   * @param brokers every broker of the cluster
   * @param nanoClock a monotonic clock in nanoseconds
   */
  SwimMembershipTracker(
      final String localMemberId, final Set<String> brokers, final LongSupplier nanoClock) {
    this.localMemberId = localMemberId;
    this.nanoClock = nanoClock;
    brokers.forEach(broker -> entries.put(broker, new Entry()));
    onAlive(localMemberId);
  }

  /** The broker is listed as reachable. */
  void onAlive(final String member) {
    final var entry = entries.get(member);
    if (entry == null) {
      return;
    }
    entry.state = ObservedState.ALIVE;
    entry.outOfSightSinceNanos = 0;
  }

  /** The broker is listed but did not answer probes. */
  void onSuspect(final String member) {
    final var entry = entries.get(member);
    if (entry == null || member.equals(localMemberId)) {
      return;
    }
    if (entry.state == ObservedState.ALIVE) {
      entry.state = ObservedState.SUSPECT;
      entry.outOfSightSinceNanos = nanoClock.getAsLong();
    }
  }

  /** The broker is no longer listed. */
  void onDead(final String member) {
    final var entry = entries.get(member);
    if (entry == null || member.equals(localMemberId)) {
      return;
    }
    if (entry.state == ObservedState.ALIVE) {
      entry.state = ObservedState.DEAD;
      entry.outOfSightSinceNanos = nanoClock.getAsLong();
    } else if (entry.state == ObservedState.SUSPECT) {
      entry.state = ObservedState.DEAD;
    }
  }

  /**
   * Aligns the view with the members SWIM lists right now, in case an event was missed or handled
   * out of order.
   *
   * @param listed the reachability of every member SWIM lists, absent members are not listed
   */
  void reconcile(final Map<String, Boolean> listed) {
    for (final var member : entries.keySet()) {
      final var reachable = listed.get(member);
      if (reachable == null) {
        onDead(member);
      } else if (reachable) {
        onAlive(member);
      } else {
        onSuspect(member);
      }
    }
  }

  /** Returns what this broker sees of every broker of the cluster, ordered by member id. */
  List<Observation> observations() {
    final var now = nanoClock.getAsLong();
    final var observations = new ArrayList<Observation>(entries.size());
    for (final var member : new TreeSet<>(entries.keySet())) {
      final var entry = entries.get(member);
      final var unreachableMillis =
          entry.state.isOutOfSight()
              ? TimeUnit.NANOSECONDS.toMillis(now - entry.outOfSightSinceNanos)
              : 0L;
      observations.add(new Observation(member, entry.state, unreachableMillis));
    }
    return observations;
  }

  private static final class Entry {
    private ObservedState state = ObservedState.UNKNOWN;
    private long outOfSightSinceNanos;
  }
}
