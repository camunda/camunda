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

/**
 * The two decision points that vary between replication signals (LSN, reported lag, a fixed delay);
 * everything else - the queue, debouncing, pausing, scheduling, metrics - is identical regardless
 * of signal and lives on {@link DefaultReplicationController}.
 */
public interface ReplicationSignalStrategy {

  /**
   * Sentinel meaning "nothing is confirmable right now" (provider unhealthy, or quorum not met).
   * Any real LSN or DB-clock-ms reading is always well above this.
   */
  long UNCONFIRMED = Long.MIN_VALUE;

  /**
   * Sentinel {@link Duration} meaning "treat as worst-case lag" (quorum not met, or a null/missing
   * per-replica lag reading) - mirrors the null-as-worst-case idiom already used for individual
   * status fields elsewhere in this codebase.
   */
  Duration PAUSE_WORST_CASE = Duration.ofMillis(Long.MAX_VALUE);

  /**
   * Captures the fallible value to tag a freshly flushed position with (an LSN, a DB-clock-ms
   * reading, or {@code now + delay}). Called once per flush; a thrown exception force-pauses the
   * exporter.
   */
  long captureFlushMarker();

  /**
   * Polls the current per-replica statuses. Called once per periodic check; the same list is fed to
   * both decision methods below and to metrics recording, so there is exactly one round trip per
   * check regardless of which strategy is wired in.
   */
  List<? extends ReplicationStatus> fetchStatuses();

  /**
   * Decision point 1 ("when to acknowledge"): the confirmation threshold. An entry is confirmed
   * once {@code entry.marker() <= computeConfirmedMarker(statuses)}. Returns {@link #UNCONFIRMED}
   * when nothing should be confirmed this round.
   */
  long computeConfirmedMarker(List<? extends ReplicationStatus> statuses);

  /**
   * Decision point 2 ("when to pause"): the current lag, compared by the shared controller against
   * {@code maxLag}. {@code queueHeadAge} is the age of the oldest still-unconfirmed queued entry,
   * computed generically from the shared controller's own clock; a strategy may fold it in (when it
   * has no other lag signal of its own) or ignore it (when it does). Returns {@link
   * #PAUSE_WORST_CASE} when quorum is not met.
   */
  Duration computePauseLag(List<? extends ReplicationStatus> statuses, Duration queueHeadAge);
}
