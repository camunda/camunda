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
   * computeConfirmedMarker(statuses)}. Returns {@link #UNCONFIRMED} when nothing is confirmed.
   */
  long computeConfirmedMarker(List<T> statuses);

  /**
   * The current replication lag, compared against {@code maxLag} to decide whether to pause. {@code
   * queueHeadAge} is the age of the oldest still-unconfirmed queued entry, or {@link
   * Optional#empty()} when the queue is empty. Returns {@link #PAUSE_WORST_CASE} when quorum is not
   * met.
   */
  Duration computePauseLag(List<T> statuses, Optional<Duration> queueHeadAge);

  /** The delay before the next periodic check. Defaults to {@code pollingInterval} unchanged. */
  default Duration nextCheckDelay(
      final Duration pollingInterval, final Optional<Duration> queueHeadAge) {
    return pollingInterval;
  }
}
