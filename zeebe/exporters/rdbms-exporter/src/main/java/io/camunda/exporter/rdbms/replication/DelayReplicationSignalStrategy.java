/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;

/**
 * Delays acknowledgment of flushed positions by a fixed duration instead of observing any actual
 * replication signal - there is no provider and no quorum concept. A position is confirmed once its
 * captured release time ({@code now + delay}, at flush time) is at or before the current time at
 * check time; the exporter never pauses, since there is no signal to judge it out of sync by.
 */
public final class DelayReplicationSignalStrategy implements ReplicationSignalStrategy {

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
  public List<? extends ReplicationStatus> fetchStatuses() {
    return List.of();
  }

  /** Ignores {@code statuses} - there is no replica signal, only the passage of time. */
  @Override
  public long computeConfirmedMarker(final List<? extends ReplicationStatus> statuses) {
    return clock.millis();
  }

  /** Never pauses - there is no replication signal to judge the exporter out of sync by. */
  @Override
  public Duration computePauseLag(
      final List<? extends ReplicationStatus> statuses, final Duration queueHeadAge) {
    return Duration.ZERO;
  }
}
