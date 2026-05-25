/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationLogStatus;
import io.camunda.db.rdbms.read.replication.ReplicationLogStatusProvider;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LsnReplicationController uses the log sequence number (LSN) of the database to compare the
 * replication status of the primary and all linked replicas with each other. We only
 * acknowledge/commit exporter positions to the ExporterController if the LSN of the replicas has
 * reached the LSN of the primary at the time of the flush. <br>
 * <br>
 * This ensures that the exporter will only acknowledge positions to the ExporterController if they
 * have been replicated to all replicas, which guarantees that no data will be lost in case of a
 * failover. The replication status is checked periodically by polling the database for the current
 * LSN of the primary and all replicas and comparing them with each other. If the replication lag
 * exceeds a configured threshold of <code>maxLag</code>, the replication is considered out-of-sync
 * and the exporter is paused until it is back in sync.<br>
 * <br>
 * Note: While the exporter position is a sequential offset per partition, the LSN is a global,
 * monotonically increasing value representing the commit order of transactions in the database. The
 * used LSN is never the actual LSN of the last exporter transaction, but it is still guaranteed
 * that it is higher, so we still can use that LSN as a guarantee for replication.
 */
public class LsnReplicationController implements ReplicationController {
  public static final int DEFAULT_QUEUE_CAPACITY = 8192;

  private static final Logger LOG = LoggerFactory.getLogger(LsnReplicationController.class);

  private final ReplicationLogStatusProvider lsnProvider;
  private final Controller controller;
  private final ReplicationConfiguration config;
  private final int partitionId;
  private final InstantSource clock;
  private final RdbmsWriterMetrics metrics;

  private final BlockingQueue<LsnPositionEntry> pendingEntries;
  private final AtomicLong flushedPosition = new AtomicLong(-1);
  private final AtomicLong replicatedPosition = new AtomicLong(-1);
  private final AtomicReference<Instant> lastConfirmedReplication;
  private final AtomicBoolean paused = new AtomicBoolean(false);

  private volatile ScheduledTask replicationCheckTask;

  public LsnReplicationController(
      final Controller controller,
      final ReplicationLogStatusProvider lsnProvider,
      final ReplicationConfiguration replicationConfiguration,
      final int partitionId,
      final InstantSource clock,
      final RdbmsWriterMetrics metrics) {
    this.lsnProvider = lsnProvider;
    this.controller = controller;
    config = replicationConfiguration;
    this.partitionId = partitionId;
    this.clock = clock;
    this.metrics = metrics;

    pendingEntries = new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    replicationCheckTask =
        controller.scheduleCancellableTask(
            replicationConfiguration.getPollingInterval(), this::checkReplication);

    lastConfirmedReplication = new AtomicReference<>(Instant.MIN);
  }

  /**
   * Tracks the current LSN and link it to the current exporter position. This pair is remembered in
   * a list until it is confirmed by the connected replicas.
   *
   * @param exporterPosition current exported position
   */
  @Override
  public void onFlush(final long exporterPosition) {
    flushedPosition.set(exporterPosition);
    try {
      final long currentLsn = lsnProvider.getCurrent();
      final long now = clock.millis();
      LOG.debug(
          "[RDBMS Exporter P{}] Flushed position {}, current LSN is {}, enqueueing for replication check",
          partitionId,
          exporterPosition,
          currentLsn);
      if (!pendingEntries.offer(new LsnPositionEntry(exporterPosition, currentLsn, now))) {
        // it's fine to just drop. When we are able to compact, when we export a new entry it will
        // be added. If the queue is not full for too much it's not a problem, otherwise we will
        // just hit the max_lag bound.
        LOG.warn(
            "[RDBMS Exporter P{}] Replication queue is full, dropping LSN entry (lsn={}, position={})",
            partitionId,
            currentLsn,
            exporterPosition);
      }
    } catch (final Exception e) {
      pauseOnFlushFailure(exporterPosition, e);
    }
  }

  @Override
  public boolean isReplicationInSync() {
    return !paused.get();
  }

  /**
   * Retrieve the replication status from the connected replicas and confirm all older pairs of
   * LSN+position to the exporter controller.<br>
   * <br>
   * If the oldest still not replicated position is older than the configured <code>maxLag</code>,
   * the replication is marked as <i>out-of-sync</i>.
   */
  @VisibleForTesting
  void checkReplication() {
    try {
      final var statuses = lsnProvider.getReplicationStatuses();
      final int connectedReplicas = statuses.size();

      final long confirmedLsn = computeConfirmedLsn(statuses);
      final var confirmedEntry = removeConfirmedLsnEntries(confirmedLsn);
      final Duration dbReplicationLag = getCurrentDbLag();

      LOG.debug(
          "[RDBMS Exporter P{}] Confirmed LSN {}, current lag is {}",
          partitionId,
          confirmedLsn,
          dbReplicationLag);

      final boolean dbLagExceeded = isMaxLagExceeded(dbReplicationLag);
      final boolean quorumNotMet =
          pendingEntries.isEmpty() && connectedReplicas < config.getMinSyncReplicas();

      // pause when:
      // - pauseOnMaxLagExceeded is true
      // - either the replicas have not confirmed for long time
      // - or there are no pending entries and there are not enough replicas connected
      updatePausedState(
          config.isPauseOnMaxLagExceeded() && (dbLagExceeded || quorumNotMet),
          dbReplicationLag,
          connectedReplicas);

      if (confirmedEntry != null) {
        replicatedPosition.set(confirmedEntry.position);
        LOG.info(
            "[RDBMS Exporter P{}] Updating replicated position to {}",
            partitionId,
            replicatedPosition.get());
        controller.updateLastExportedRecordPosition(replicatedPosition.get());
      }

      metrics.recordReplicationStatus(
          statuses, paused.get(), flushedPosition.get(), replicatedPosition.get());
    } catch (final Exception e) {
      LOG.error(
          "[RDBMS Exporter P{}] Error while checking replication status, will retry after {}",
          partitionId,
          config.getPollingInterval(),
          e);
    } finally {
      // if null, controller was closed during check
      if (replicationCheckTask != null) {
        replicationCheckTask =
            controller.scheduleCancellableTask(config.getPollingInterval(), this::checkReplication);
      }
    }
  }

  @VisibleForTesting
  Duration getCurrentDbLag() {
    if (pendingEntries.isEmpty()) {
      return Duration.ZERO;
    }
    return Duration.ofMillis(clock.millis() - pendingEntries.peek().enqueueTimeMs);
  }

  @VisibleForTesting
  boolean isMaxLagExceeded(final Duration dbLag) {
    return dbLag.compareTo(config.getMaxLag()) > 0;
  }

  /**
   * Removes entries from the queue which have been confirmed based on the confirmedLsn.
   *
   * @param confirmedLsn the confirmed LSN based on the database
   * @return the highest confirmed entry
   */
  @VisibleForTesting
  LsnPositionEntry removeConfirmedLsnEntries(final long confirmedLsn) {
    LsnPositionEntry lastConfirmedEntry = null;
    LsnPositionEntry entry;
    long newReplicatedPosition = replicatedPosition.get();
    while ((entry = pendingEntries.peek()) != null) {
      if (entry.lsn() > confirmedLsn) {
        break;
      }

      LOG.trace(
          "[RDBMS Exporter P{}] Removing pending LSN {} for position {}, as it is lower than confirmed lsn {}",
          partitionId,
          entry.lsn(),
          entry.position(),
          confirmedLsn);

      lastConfirmedReplication.set(Instant.ofEpochMilli(entry.enqueueTimeMs()));
      newReplicatedPosition = entry.position();
      lastConfirmedEntry = pendingEntries.poll();
    }
    if (newReplicatedPosition > replicatedPosition.get()) {
      return lastConfirmedEntry;
    }

    return null;
  }

  private void pauseOnFlushFailure(final long exporterPosition, final Exception e) {
    paused.set(true);
    LOG.error(
        "[RDBMS Exporter P{}] Failed to capture replication state after flushing exporter "
            + "position {}. Exporting will remain paused until replication checks recover.",
        partitionId,
        exporterPosition,
        e);
  }

  private void updatePausedState(
      final boolean shouldPause, final Duration dbReportedLag, final int connectedReplicas) {
    final boolean wasPaused = paused.getAndSet(shouldPause);
    if (shouldPause && !wasPaused) {
      LOG.warn(
          "[RDBMS Exporter P{}] Pausing exporter: replication lag ({}) exceeded maxLag ({})",
          partitionId,
          dbReportedLag,
          config.getMaxLag());
    } else if (!shouldPause && wasPaused) {
      LOG.info(
          "[RDBMS Exporter P{}] Resuming exporter: replication quorum met ({}/{} replicas) and lag ({}) within maxLag ({})",
          partitionId,
          connectedReplicas,
          config.getMinSyncReplicas(),
          dbReportedLag,
          config.getMaxLag());
    }
  }

  /**
   * Calculates the lowest LSN which is confirmed by at least the configured <code>minSyncReplicas
   * </code> connected replicas.<br>
   * The number of entries in <code>statuses</code> may vary over time since replicas may disconnect
   * on network failure or when new, optional replicas are connected. The <code>minSyncReplicas
   * </code> configuration property defines a minimal quorum of replicas in sync.
   *
   * @param statuses the replication status read from all connected replicas.
   * @return the lowest LSN confirmed.
   */
  @VisibleForTesting
  long computeConfirmedLsn(final List<ReplicationLogStatus> statuses) {
    if (lsnProvider.getCurrent() < 0) {
      return Long.MIN_VALUE;
    }

    if (statuses.size() < config.getMinSyncReplicas()) {
      return -1;
    }

    return statuses.stream()
        .map(ReplicationLogStatus::logStatus)
        .sorted(Comparator.<Long>naturalOrder().reversed())
        .limit(config.getMinSyncReplicas())
        .min(Comparator.naturalOrder())
        .orElse(-1L);
  }

  @Override
  public void close() throws Exception {
    replicationCheckTask.cancel();
    replicationCheckTask = null;
  }

  /**
   * An entry linking an LSN to an exporter position.
   *
   * @param position the exporter position
   * @param lsn the LSN
   * @param enqueueTimeMs the instant in ms when the LSN was queried
   */
  record LsnPositionEntry(long position, long lsn, long enqueueTimeMs) {}
}
