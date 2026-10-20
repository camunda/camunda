/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which workers of a group are live, as one worker sees them: a worker is live from the first time
 * it is seen, and stays live while its beat keeps changing. Once its beat hasn't changed for a
 * lease, timed on the observer's own clock, it is dead. A worker whose heartbeat object is gone is
 * gone at once. No time that another worker wrote is ever compared. Not thread-safe.
 */
final class Liveness {

  private final long leaseNanos;
  private final Map<String, Seen> seen = new HashMap<>();

  Liveness(final Duration lease) {
    leaseNanos = lease.toNanos();
  }

  /**
   * Takes what a listing of the heartbeats showed.
   *
   * @param listed the workers whose heartbeat objects the listing showed
   * @param beats the beats of those whose objects could be read; the others keep their last
   * @param now the observer's monotonic clock, in nanoseconds
   */
  void observe(final Set<String> listed, final Map<String, Long> beats, final long now) {
    seen.keySet().retainAll(listed);
    beats.forEach(
        (worker, beat) -> {
          final var last = seen.get(worker);
          if (last == null || last.beat() != beat) {
            seen.put(worker, new Seen(beat, now));
          }
        });
  }

  boolean isLive(final String worker, final long now) {
    final var last = seen.get(worker);
    return last != null && now - last.changedAt() < leaseNanos;
  }

  /** The live workers, sorted. */
  Set<String> live(final long now) {
    final var live = new TreeSet<String>();
    seen.forEach(
        (worker, last) -> {
          if (now - last.changedAt() < leaseNanos) {
            live.add(worker);
          }
        });
    return live;
  }

  /** The workers whose heartbeat hasn't changed for this long, on this worker's own clock. */
  Set<String> deadFor(final Duration time, final long now) {
    final var dead = new TreeSet<String>();
    seen.forEach(
        (worker, last) -> {
          if (now - last.changedAt() >= time.toNanos()) {
            dead.add(worker);
          }
        });
    return dead;
  }

  private record Seen(long beat, long changedAt) {}
}
