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
import io.camunda.db.rdbms.read.replication.ReplicationStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Monitors the replication lag reported directly by the database (in milliseconds). Unlike {@link
 * LsnReplicationSignalStrategy}, this strategy does not track log-sequence numbers, only a
 * per-replica lag figure and an absolute as-of point in time - safe to use for confirmation even if
 * the underlying signal goes stale, since it doesn't drift with the wall clock the way "now minus a
 * relative lag" would.
 */
public final class TimeMonitoringReplicationSignalStrategy implements ReplicationSignalStrategy {

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
   * replicas, mirroring {@link LsnReplicationSignalStrategy}'s LSN-based computation. Returns
   * {@link #UNCONFIRMED} when quorum isn't met.
   */
  @Override
  public long computeConfirmedMarker(final List<? extends ReplicationStatus> statuses) {
    if (statuses.size() < config.getMinSyncReplicas()) {
      return UNCONFIRMED;
    }
    return statuses.stream()
        .map(ReplicationLagStatus.class::cast)
        .map(s -> s.replicatedUntilMs() != null ? s.replicatedUntilMs() : UNCONFIRMED)
        .sorted(Comparator.<Long>naturalOrder().reversed())
        .limit(config.getMinSyncReplicas())
        .min(Comparator.naturalOrder())
        .orElse(UNCONFIRMED);
  }

  /**
   * The maximum replication lag reported across all replicas; ignores {@code queueHeadAge}, since
   * this mode has its own replica-reported lag signal. A null per-replica lag is treated as
   * worst-case, never as zero.
   */
  @Override
  public Duration computePauseLag(
      final List<? extends ReplicationStatus> statuses, final Duration queueHeadAge) {
    if (statuses.size() < config.getMinSyncReplicas()) {
      return PAUSE_WORST_CASE;
    }
    return statuses.stream()
        .mapToLong(s -> s.replicationLagMs() != null ? s.replicationLagMs() : Long.MAX_VALUE)
        .max()
        .stream()
        .mapToObj(Duration::ofMillis)
        .findFirst()
        .orElse(Duration.ZERO);
  }
}
