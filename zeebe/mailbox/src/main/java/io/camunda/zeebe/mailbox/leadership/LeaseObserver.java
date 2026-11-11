/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Detects failed leaders from the lease rows, without trusting any clock but its own.
 *
 * <p>For each partition it remembers the term and counter it last saw, and since when, on its own
 * monotonic clock. A lease whose term and counter have not changed for the lease duration has run
 * out. A release increases the counter, so a released lease runs out one lease duration after the
 * release; the {@link ClaimPolicy} decides who may claim it sooner. After a failed read, every
 * observation starts again, so errors of the database, for example during its failover, never count
 * as a failed leader.
 *
 * <p>Not thread-safe. The elector calls it from its own thread.
 */
final class LeaseObserver {

  private final long leaseNanos;
  private final Map<Integer, Observation> observations = new HashMap<>();

  LeaseObserver(final Duration leaseDuration) {
    leaseNanos = leaseDuration.toNanos();
  }

  /** Takes in a read of all lease rows, made at {@code nowNanos}. */
  void observe(final Collection<PartitionLease> leases, final long nowNanos) {
    final var seen = new HashSet<Integer>();
    for (final var lease : leases) {
      seen.add(lease.partitionId());
      final var previous = observations.get(lease.partitionId());
      if (previous == null
          || previous.term() != lease.term()
          || previous.counter() != lease.renewalCounter()) {
        observations.put(
            lease.partitionId(), new Observation(lease.term(), lease.renewalCounter(), nowNanos));
      }
    }
    observations.keySet().retainAll(seen);
  }

  /** Forgets all observations, because a read failed. */
  void reset() {
    observations.clear();
  }

  /** Tells whether the lease of the partition has run out, as far as this node has seen. */
  boolean hasRunOut(final int partitionId, final long nowNanos) {
    final var observation = observations.get(partitionId);
    if (observation == null) {
      return false;
    }
    return nowNanos - observation.sinceNanos() >= leaseNanos;
  }

  /**
   * Since when, on the monotonic clock, the lease of the partition has had the term and counter it
   * has now, or -1 if this node has not seen it since the last reset.
   */
  long unchangedSinceNanos(final int partitionId) {
    final var observation = observations.get(partitionId);
    return observation == null ? -1 : observation.sinceNanos();
  }

  private record Observation(long term, long counter, long sinceNanos) {}
}
