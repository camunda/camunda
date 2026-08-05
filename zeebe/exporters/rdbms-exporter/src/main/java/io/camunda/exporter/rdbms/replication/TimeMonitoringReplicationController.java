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
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.List;

/**
 * The TimeMonitoringReplicationController monitors the replication lag reported directly by the
 * database (in milliseconds) and applies backpressure whenever the lag exceeds a configured
 * threshold. Unlike {@link LsnReplicationController}, this controller does <em>not</em> track
 * log-sequence numbers, only a per-replica lag figure. This works for any {@link
 * io.camunda.db.rdbms.read.replication.ReplicationLagProvider}, including one derived from an
 * LSN-capable database via {@code LsnBackedReplicationLagProvider}.
 */
public class TimeMonitoringReplicationController
    extends AbstractReplicationController<TimeMonitoringReplicationController.LagPositionEntry> {

  private final ReplicationLagProvider statusProvider;

  public TimeMonitoringReplicationController(
      final Controller controller,
      final ReplicationLagProvider statusProvider,
      final ReplicationConfiguration config,
      final int partitionId,
      final InstantSource clock,
      final RdbmsWriterMetrics metrics) {
    super(controller, config, partitionId, clock, metrics);
    this.statusProvider = statusProvider;
  }

  /**
   * Records the latest flushed position, together with the primary's current point in time as
   * observed by the database itself. The position will be acknowledged to the broker once a
   * subsequent replication check determines it is old enough to be covered by the replicas'
   * confirmed as-of point.
   */
  @Override
  public void onFlush(final long exporterPosition) {
    onFlushCapturing(
        exporterPosition,
        statusProvider::getCurrentDbTime,
        currentDbTime -> new LagPositionEntry(exporterPosition, currentDbTime));
  }

  /**
   * Queries the database for the current per-replica replication lag and as-of point, acknowledges
   * every queued position covered by the confirmed as-of point, and updates the paused state
   * accordingly.
   */
  @Override
  protected void doCheckReplication() {
    final List<ReplicationLagStatus> statuses = statusProvider.getReplicationStatuses();
    final int connectedReplicas = statuses.size();
    final boolean quorumNotMet = connectedReplicas < config.getMinSyncReplicas();

    final Duration maxReplicaLag = computeMaxReplicaLag(statuses);
    final long confirmedAsOfMs = computeConfirmed(statuses);
    log.debug(
        "[RDBMS Exporter P{}] connectedReplicas={}, quorumNotMet={}, maxReplicaLag={}, "
            + "confirmedAsOfMs={}, statuses={}",
        partitionId,
        connectedReplicas,
        quorumNotMet,
        maxReplicaLag,
        confirmedAsOfMs,
        statuses);
    final var confirmedEntry =
        quorumNotMet ? null : drainConfirmed(entry -> entry.enqueueTimeMs() <= confirmedAsOfMs);

    final boolean lagExceeded = maxReplicaLag.compareTo(config.getMaxLag()) > 0;
    updatePausedState(
        config.isPauseOnMaxLagExceeded() && (lagExceeded || quorumNotMet),
        maxReplicaLag,
        connectedReplicas);

    if (confirmedEntry != null) {
      acknowledge(confirmedEntry, maxReplicaLag, connectedReplicas);
    }

    recordMetrics(statuses);
  }

  /**
   * Computes the maximum replication lag reported across all replicas. Returns {@link
   * Duration#ZERO} when no replicas are connected. Drives only the pause decision, not confirmation
   * (see {@link #computeConfirmed}).
   */
  @VisibleForTesting
  Duration computeMaxReplicaLag(final List<ReplicationLagStatus> statuses) {
    return statuses.stream()
        .mapToLong(s -> s.replicationLagMs() != null ? s.replicationLagMs() : Long.MAX_VALUE)
        .max()
        .stream()
        .mapToObj(Duration::ofMillis)
        .findFirst()
        .orElse(Duration.ZERO);
  }

  /**
   * Computes the point in time, as observed by the database, up to which enough reporting replicas
   * have confirmed applying.
   */
  @VisibleForTesting
  long computeConfirmed(final List<ReplicationLagStatus> statuses) {
    if (statuses.size() < config.getMinSyncReplicas()) {
      return Long.MIN_VALUE;
    }
    return statuses.stream()
        .map(s -> s.replicatedUntilMs() != null ? s.replicatedUntilMs() : Long.MIN_VALUE)
        .sorted(Comparator.<Long>naturalOrder().reversed())
        .limit(config.getMinSyncReplicas())
        .min(Comparator.naturalOrder())
        .orElse(Long.MIN_VALUE);
  }

  /**
   * An entry linking a flushed exporter position to the time it was flushed.
   *
   * @param position the exporter position
   * @param enqueueTimeMs the instant in ms when the position was flushed
   */
  record LagPositionEntry(long position, long enqueueTimeMs) implements PendingEntry {}
}
