/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The decision points that vary between replication signals (LSN, reported lag, a fixed delay).
 * Everything else - the queue, debouncing, pausing, scheduling, metrics - lives on {@link
 * DefaultReplicationController}.
 */
public interface ReplicationSignalStrategy<T extends ReplicationStatus> {

  /**
   * Sentinel meaning "nothing is confirmable right now" (provider unhealthy, or quorum not met).
   * Any real LSN or DB-clock-ms reading is always well above this.
   */
  long UNCONFIRMED = Long.MIN_VALUE;

  /** Sentinel {@link Duration} meaning "treat as worst-case lag". */
  Duration PAUSE_WORST_CASE = Duration.ofMillis(Long.MAX_VALUE);

  /**
   * Captures the value that will later prove a freshly flushed position has been safely replicated
   * (an LSN, a DB-clock-ms reading, or {@code now + delay}). Throwing pauses the exporter.
   */
  long captureFlushMarker();

  /** Returns the current per-replica replication statuses. */
  List<T> fetchStatuses();

  /**
   * The confirmation threshold: an entry is confirmed once {@code entry.marker() <=
   * computeConfirmedMarker(statuses, currentPrimaryRegion)}. Returns {@link #UNCONFIRMED} when
   * nothing is confirmed. {@code currentPrimaryRegion} is {@link #resolveCurrentPrimaryRegion()}'s
   * result for this same check - resolved once per tick by the caller, not re-resolved here.
   */
  long computeConfirmedMarker(List<T> statuses, Optional<String> currentPrimaryRegion);

  /**
   * The current replication lag, compared against {@code maxLag} to decide whether to pause. {@code
   * queueHeadAge} is the age of the oldest still-unconfirmed queued entry, or {@link
   * Optional#empty()} when the queue is empty. Returns {@link #PAUSE_WORST_CASE} when quorum is not
   * met. {@code currentPrimaryRegion} is {@link #resolveCurrentPrimaryRegion()}'s result for this
   * same check - resolved once per tick by the caller, not re-resolved here.
   */
  Duration computePauseLag(
      List<T> statuses, Optional<Duration> queueHeadAge, Optional<String> currentPrimaryRegion);

  /** The delay before the next periodic check. Defaults to {@code pollingInterval} unchanged. */
  default Duration nextCheckDelay(
      final Duration pollingInterval, final Optional<Duration> queueHeadAge) {
    return pollingInterval;
  }

  /**
   * The region hosting the primary right now, resolved fresh from its live connection every call
   * (never from static config, never cached) so it reflects a failover promptly - see {@code
   * getCurrentReplicaLabel()} on the {@code db/rdbms} providers. Called once per periodic check and
   * the result threaded into {@link #computeConfirmedMarker}, {@link #computePauseLag}, and {@link
   * #regionsBelowQuorum}, so a single tick only pays for the underlying DB read once. Defaults to
   * empty for strategies with no region concept.
   */
  default Optional<String> resolveCurrentPrimaryRegion() {
    return Optional.empty();
  }

  /**
   * The names of mandatory regions currently short of their own {@code minReplicas}, for diagnostic
   * logging when the exporter pauses. Empty when region awareness is disabled or every declared
   * region meets its own quorum. Defaults to always-empty for strategies with no region concept.
   */
  default List<String> regionsBelowQuorum(
      final List<T> statuses, final Optional<String> currentPrimaryRegion) {
    return List.of();
  }
}
