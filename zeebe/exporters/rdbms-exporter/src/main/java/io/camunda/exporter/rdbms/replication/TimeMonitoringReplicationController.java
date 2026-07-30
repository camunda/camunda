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
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The TimeMonitoringReplicationController monitors the replication lag reported directly by the
 * database (in milliseconds) and applies backpressure whenever the lag exceeds a configured
 * threshold. Unlike {@link LsnReplicationController}, this controller does <em>not</em> track
 * log-sequence numbers, only a per-replica lag figure — trading the exact position-confirmation
 * guarantee of {@link LsnReplicationController} for a simpler, coarser lag check. This works for
 * any {@link io.camunda.db.rdbms.read.replication.ReplicationLagProvider}, including one derived
 * from an LSN-capable database via {@code LsnBackedReplicationLagProvider}.
 *
 * <p>Behaviour:
 *
 * <ul>
 *   <li>On each flush the latest exported position is recorded.
 *   <li>A periodic check queries the database for per-replica lag values.
 *   <li>If the maximum lag across all replicas is within {@code maxLag} <em>and</em> at least
 *       {@code minSyncReplicas} replicas are connected, the latest flushed position is acknowledged
 *       to the {@link Controller} and the exporter is considered in-sync.
 *   <li>Otherwise the exporter is paused: {@link #isReplicationInSync()} returns {@code false},
 *       causing {@link io.camunda.exporter.rdbms.RdbmsExporter#export} to throw an {@link
 *       io.camunda.zeebe.exporter.api.ExporterException} which triggers flow-control backpressure.
 * </ul>
 */
public class TimeMonitoringReplicationController implements ReplicationController {

  private static final Logger LOG =
      LoggerFactory.getLogger(TimeMonitoringReplicationController.class);

  private final ReplicationLagProvider statusProvider;
  private final Controller controller;
  private final ReplicationConfiguration config;
  private final int partitionId;
  private final RdbmsWriterMetrics metrics;

  private final AtomicLong latestFlushedPosition = new AtomicLong(-1);
  private final AtomicLong acknowledgedPosition = new AtomicLong(-1);
  private final AtomicBoolean paused = new AtomicBoolean(false);

  private volatile ScheduledTask replicationCheckTask;

  public TimeMonitoringReplicationController(
      final Controller controller,
      final ReplicationLagProvider statusProvider,
      final ReplicationConfiguration config,
      final int partitionId,
      final RdbmsWriterMetrics metrics) {
    this.statusProvider = statusProvider;
    this.controller = controller;
    this.config = config;
    this.partitionId = partitionId;
    this.metrics = metrics;

    replicationCheckTask =
        controller.scheduleCancellableTask(config.getPollingInterval(), this::checkReplication);
  }

  /**
   * Records the latest flushed position. The position will be acknowledged to the broker once a
   * subsequent replication check confirms acceptable lag.
   */
  @Override
  public void onFlush(final long exporterPosition) {
    latestFlushedPosition.set(exporterPosition);
  }

  @Override
  public boolean isReplicationInSync() {
    return !paused.get();
  }

  /**
   * Queries the database for the current per-replica replication lag and updates the paused state
   * and the acknowledged exporter position accordingly.
   */
  @VisibleForTesting
  void checkReplication() {
    try {
      final List<ReplicationLagStatus> statuses = statusProvider.getReplicationStatuses();
      final int connectedReplicas = statuses.size();

      final Duration maxReplicaLag = computeMaxReplicaLag(statuses);
      final boolean lagExceeded = maxReplicaLag.compareTo(config.getMaxLag()) > 0;
      final boolean quorumNotMet = connectedReplicas < config.getMinSyncReplicas();
      final boolean isSafeToAcknowledge = !lagExceeded && !quorumNotMet;

      final boolean shouldPause = config.isPauseOnMaxLagExceeded() && !isSafeToAcknowledge;
      updatePausedState(shouldPause, maxReplicaLag, connectedReplicas);

      if (isSafeToAcknowledge) {
        final long position = latestFlushedPosition.get();
        if (position > acknowledgedPosition.get()) {
          acknowledgedPosition.set(position);
          LOG.info(
              "[RDBMS Exporter P{}] Acknowledging position {} (replication lag: {}, replicas: {})",
              partitionId,
              position,
              maxReplicaLag,
              connectedReplicas);
          controller.updateLastExportedRecordPosition(position);
        }
      }

      metrics.recordReplicationStatus(
          statuses, paused.get(), latestFlushedPosition.get(), acknowledgedPosition.get());
    } catch (final Exception e) {
      LOG.error(
          "[RDBMS Exporter P{}] Error while checking replication lag, will retry after {}",
          partitionId,
          config.getPollingInterval(),
          e);
    } finally {
      if (replicationCheckTask != null) {
        replicationCheckTask =
            controller.scheduleCancellableTask(config.getPollingInterval(), this::checkReplication);
      }
    }
  }

  /**
   * Computes the maximum replication lag reported across all replicas. Returns {@link
   * Duration#ZERO} when no replicas are connected.
   */
  @VisibleForTesting
  Duration computeMaxReplicaLag(final List<ReplicationLagStatus> statuses) {
    return statuses.stream()
        .mapToLong(s -> s.replicationLagMs() != null ? s.replicationLagMs() : 0L)
        .max()
        .stream()
        .mapToObj(Duration::ofMillis)
        .findFirst()
        .orElse(Duration.ZERO);
  }

  private void updatePausedState(
      final boolean shouldPause, final Duration maxLag, final int connectedReplicas) {
    final boolean wasPaused = paused.getAndSet(shouldPause);
    if (shouldPause && !wasPaused) {
      LOG.warn(
          "[RDBMS Exporter P{}] Pausing exporter: replication lag ({}) exceeded maxLag ({}) "
              + "or quorum not met ({}/{} replicas)",
          partitionId,
          maxLag,
          config.getMaxLag(),
          connectedReplicas,
          config.getMinSyncReplicas());
    } else if (!shouldPause && wasPaused) {
      LOG.info(
          "[RDBMS Exporter P{}] Resuming exporter: replication lag ({}) within maxLag ({}) "
              + "and quorum met ({}/{} replicas)",
          partitionId,
          maxLag,
          config.getMaxLag(),
          connectedReplicas,
          config.getMinSyncReplicas());
    }
  }

  @Override
  public void close() throws Exception {
    replicationCheckTask.cancel();
    replicationCheckTask = null;
  }
}
