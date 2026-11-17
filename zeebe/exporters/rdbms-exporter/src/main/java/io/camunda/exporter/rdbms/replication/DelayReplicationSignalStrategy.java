/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationLagStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;

/**
 * Delays acknowledgment of flushed positions by a fixed duration instead of observing any actual
 * replication signal - there is no provider and no quorum concept. A position is confirmed once its
 * captured release time ({@code now + delay}, at flush time) is at or before the current time at
 * check time; the exporter never pauses, since there is no signal to judge it out of sync by.
 */
public final class DelayReplicationSignalStrategy
    implements ReplicationSignalStrategy<ReplicationLagStatus> {

  private final ReplicationConfiguration config;
  private final InstantSource clock;

  public DelayReplicationSignalStrategy(
      final ReplicationConfiguration config, final InstantSource clock) {
    this.config = config;
    this.clock = clock;
  }

  @Override
  public long captureFlushMarker() {
    return clock.millis() + config.getDelay().toMillis();
  }

  @Override
  public List<ReplicationLagStatus> fetchStatuses() {
    return List.of();
  }

  /** Ignores {@code statuses} - there is no replica signal, only the passage of time. */
  @Override
  public long computeConfirmedMarker(final List<ReplicationLagStatus> statuses) {
    return clock.millis();
  }

  /** Never pauses - there is no replication signal to judge the exporter out of sync by. */
  @Override
  public Duration computePauseLag(
      final List<ReplicationLagStatus> statuses, final Optional<Duration> queueHeadAge) {
    return Duration.ZERO;
  }

  /**
   * Wakes up exactly when the oldest queued entry's release time ({@code enqueueTime + delay}) is
   * due, rather than on a fixed cadence. Waits the full {@code delay} when nothing is queued.
   */
  @Override
  public Duration nextCheckDelay(
      final Duration pollingInterval, final Optional<Duration> queueHeadAge) {
    final long remainingMs =
        config.getDelay().toMillis() - queueHeadAge.orElse(Duration.ZERO).toMillis();
    return Duration.ofMillis(Math.max(1, remainingMs));
  }
}
