/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationLagProvider;
import io.camunda.db.rdbms.read.replication.ReplicationLagStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Monitors the replication lag reported directly by the database (in milliseconds), together with
 * an absolute as-of point in time for confirmation - stable even if the underlying signal goes
 * stale, since it doesn't drift with the wall clock the way "now minus a relative lag" would.
 */
public final class TimeMonitoringReplicationSignalStrategy
    implements ReplicationSignalStrategy<ReplicationLagStatus> {

  private final ReplicationLagProvider statusProvider;
  private final ReplicationConfiguration config;

  public TimeMonitoringReplicationSignalStrategy(
      final ReplicationLagProvider statusProvider, final ReplicationConfiguration config) {
    this.statusProvider = statusProvider;
    this.config = config;
  }

  @Override
  public long captureFlushMarker() {
    return statusProvider.getCurrentDbTime();
  }

  @Override
  public List<ReplicationLagStatus> fetchStatuses() {
    return statusProvider.getReplicationStatuses();
  }

  /**
   * The point in time, as observed by the database, up to which at least {@code minSyncReplicas}
   * replicas have confirmed applying - the lowest as-of value among the top {@code minSyncReplicas}
   * replicas. Returns {@link #UNCONFIRMED} when quorum isn't met.
   */
  @Override
  public long computeConfirmedMarker(final List<ReplicationLagStatus> statuses) {
    if (statuses.size() < config.getMinSyncReplicas()) {
      return UNCONFIRMED;
    }
    return statuses.stream()
        .map(s -> s.replicatedUntilMs() != null ? s.replicatedUntilMs() : UNCONFIRMED)
        .sorted(Comparator.<Long>naturalOrder().reversed())
        .limit(config.getMinSyncReplicas())
        .min(Comparator.naturalOrder())
        .orElse(UNCONFIRMED);
  }

  /**
   * The worst replication lag among the {@code minSyncReplicas} most caught-up replicas when quorum
   * is met. When quorum is not met, falls back to {@code queueHeadAge} - how long the oldest
   * still-unconfirmed position has been waiting - so a replica shortage is graced by {@code maxLag}
   * the same way a healthy-but-slow replica would be, rather than pausing immediately; returns
   * {@link #PAUSE_WORST_CASE} only once the queue is also empty, since there is then no staleness
   * signal to measure by. A null per-replica lag is treated as worst-case, never as zero.
   */
  @Override
  public Duration computePauseLag(
      final List<ReplicationLagStatus> statuses, final Optional<Duration> queueHeadAge) {
    if (statuses.size() < config.getMinSyncReplicas()) {
      return queueHeadAge.orElse(PAUSE_WORST_CASE);
    }
    return statuses.stream()
        .mapToLong(s -> s.replicationLagMs() != null ? s.replicationLagMs() : Long.MAX_VALUE)
        .sorted() // ascending: lowest (best) lag first
        .limit(config.getMinSyncReplicas()) // the minSyncReplicas most caught-up replicas
        .max() // the worst among just those
        .stream()
        .mapToObj(Duration::ofMillis)
        .findFirst()
        .orElse(Duration.ZERO);
  }
}
