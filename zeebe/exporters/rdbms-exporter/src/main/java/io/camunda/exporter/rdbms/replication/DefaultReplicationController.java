/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.read.replication.ReplicationStatus;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queues exporter positions flushed via {@link #onFlush(long)} until a {@link
 * ReplicationSignalStrategy}-specific condition proves them safely replicated, then acknowledges
 * the highest confirmed position to the {@link Controller}. A periodic check polls the strategy's
 * replication signal to determine what is confirmed and whether the exporter should be paused.
 */
public final class DefaultReplicationController implements ReplicationController {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Controller controller;
  private final ReplicationSignalStrategy strategy;
  private final ReplicationConfiguration config;
  private final int partitionId;
  private final InstantSource clock;
  private final RdbmsWriterMetrics metrics;
  private final AtomicLong flushedPosition = new AtomicLong(-1);
  private final AtomicLong acknowledgedPosition = new AtomicLong(-1);

  private final BlockingQueue<QueuedPosition> pendingEntries;
  private final long queueDebounceMillis;
  private long lastAdded = Long.MIN_VALUE;
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private volatile ScheduledTask replicationCheckTask;

  public DefaultReplicationController(
      final Controller controller,
      final ReplicationSignalStrategy strategy,
      final ReplicationConfiguration config,
      final int partitionId,
      final InstantSource clock,
      final RdbmsWriterMetrics metrics) {
    this.controller = controller;
    this.strategy = strategy;
    this.config = config;
    this.partitionId = partitionId;
    this.clock = clock;
    this.metrics = metrics;

    queueDebounceMillis = config.getQueueDebounceTime().toMillis();
    pendingEntries = new ArrayBlockingQueue<>(config.getQueueCapacity());
    replicationCheckTask =
        controller.scheduleCancellableTask(config.getPollingInterval(), this::checkReplication);
  }

  /**
   * Records the flushed position and enqueues it for confirmation. On failure, force-pauses the
   * exporter and logs instead of propagating.
   */
  @Override
  public void onFlush(final long exporterPosition) {
    flushedPosition.set(exporterPosition);
    try {
      enqueue(exporterPosition);
    } catch (final Exception e) {
      paused.set(true);
      log.error(
          "[RDBMS Exporter P{}] Failed to capture replication state after flushing exporter "
              + "position {}. Exporting will remain paused until replication checks recover.",
          partitionId,
          exporterPosition,
          e);
    }
  }

  @Override
  public boolean isReplicationInSync() {
    return !paused.get();
  }

  /**
   * Enqueues the flushed position for later confirmation. Drops it if the previous entry was added
   * less than {@code queueDebounceTime} ago, or if the queue is full.
   */
  private void enqueue(final long exporterPosition) {
    final long now = clock.millis();

    if (queueDebounceMillis > 0
        && !pendingEntries.isEmpty()
        && now - lastAdded < queueDebounceMillis) {
      log.debug(
          "[RDBMS Exporter P{}] Debouncing flush (position={}), last added {} ms ago",
          partitionId,
          exporterPosition,
          now - lastAdded);
      return;
    }

    if (pendingEntries.remainingCapacity() == 0) {
      log.warn(
          "[RDBMS Exporter P{}] Replication queue is full, dropping position entry (position={})",
          partitionId,
          exporterPosition);
      return;
    }

    final long marker = strategy.captureFlushMarker();
    log.debug(
        "[RDBMS Exporter P{}] Flushed position {}, captured replication marker {}, enqueueing "
            + "for replication check",
        partitionId,
        exporterPosition,
        marker);

    if (!pendingEntries.offer(new QueuedPosition(exporterPosition, marker, now))) {
      log.warn(
          "[RDBMS Exporter P{}] Replication queue is full, dropping position entry (position={})",
          partitionId,
          exporterPosition);
    } else {
      lastAdded = now;
    }
  }

  /**
   * Polls the strategy for the current replication signal, confirms queued positions covered by the
   * resulting threshold, and updates the paused state accordingly.
   */
  @VisibleForTesting
  final void checkReplication() {
    try {
      final List<? extends ReplicationStatus> statuses = strategy.fetchStatuses();
      final int connectedReplicas = statuses.size();

      final long confirmedMarker = strategy.computeConfirmedMarker(statuses);
      final QueuedPosition confirmedEntry = drainConfirmed(confirmedMarker);

      final Optional<Duration> queueHeadAge = queueHeadAge();
      final Duration pauseLag = strategy.computePauseLag(statuses, queueHeadAge);

      log.debug(
          "[RDBMS Exporter P{}] connectedReplicas={}, confirmedMarker={}, pauseLag={}, statuses={}",
          partitionId,
          connectedReplicas,
          confirmedMarker,
          pauseLag,
          statuses);

      updatePausedState(
          config.isPauseOnMaxLagExceeded() && pauseLag.compareTo(config.getMaxLag()) > 0,
          pauseLag,
          connectedReplicas);

      if (confirmedEntry != null) {
        acknowledge(confirmedEntry, pauseLag, connectedReplicas);
      }

      metrics.recordReplicationStatus(
          statuses, paused.get(), flushedPosition.get(), acknowledgedPosition.get());
    } catch (final Exception e) {
      log.error(
          "[RDBMS Exporter P{}] Error while checking replication status, will retry after {}",
          partitionId,
          config.getPollingInterval(),
          e);
    } finally {
      // if null, controller was closed during check
      if (replicationCheckTask != null) {
        final Duration nextDelay =
            strategy.nextCheckDelay(config.getPollingInterval(), queueHeadAge());
        replicationCheckTask =
            controller.scheduleCancellableTask(nextDelay, this::checkReplication);
      }
    }
  }

  /**
   * The age of the oldest still-unconfirmed queued entry, or {@link Optional#empty()} if the queue
   * is empty.
   */
  @VisibleForTesting
  Optional<Duration> queueHeadAge() {
    final QueuedPosition head = pendingEntries.peek();
    if (head == null) {
      return Optional.empty();
    }
    return Optional.of(Duration.ofMillis(clock.millis() - head.enqueueTimeMs()));
  }

  /**
   * Removes every queued entry whose marker is at or below {@code confirmedMarker} from the front
   * of the queue.
   *
   * @return the highest-position entry removed, or {@code null} if nothing advanced past the
   *     currently acknowledged position
   */
  @VisibleForTesting
  QueuedPosition drainConfirmed(final long confirmedMarker) {
    QueuedPosition lastConfirmedEntry = null;
    long newAcknowledgedPosition = acknowledgedPosition.get();
    QueuedPosition entry;
    while ((entry = pendingEntries.peek()) != null) {
      if (entry.marker() > confirmedMarker) {
        break;
      }
      newAcknowledgedPosition = entry.position();
      lastConfirmedEntry = pendingEntries.poll();
    }
    if (newAcknowledgedPosition > acknowledgedPosition.get()) {
      return lastConfirmedEntry;
    }
    return null;
  }

  private void acknowledge(
      final QueuedPosition entry, final Duration replicationLag, final int connectedReplicas) {
    acknowledgedPosition.set(entry.position());
    log.info(
        "[RDBMS Exporter P{}] Acknowledging position {} (replication lag: {}, replicas: {})",
        partitionId,
        entry.position(),
        replicationLag,
        connectedReplicas);
    controller.updateLastExportedRecordPosition(entry.position());
  }

  private void updatePausedState(
      final boolean shouldPause, final Duration replicationLag, final int connectedReplicas) {
    final boolean wasPaused = paused.getAndSet(shouldPause);
    if (shouldPause && !wasPaused) {
      log.warn(
          "[RDBMS Exporter P{}] Pausing exporter: replication lag ({}) exceeded maxLag ({}) "
              + "or quorum not met ({}/{} replicas)",
          partitionId,
          replicationLag,
          config.getMaxLag(),
          connectedReplicas,
          config.getMinSyncReplicas());
    } else if (!shouldPause && wasPaused) {
      log.info(
          "[RDBMS Exporter P{}] Resuming exporter: replication lag ({}) within maxLag ({}) "
              + "and quorum met ({}/{} replicas)",
          partitionId,
          replicationLag,
          config.getMaxLag(),
          connectedReplicas,
          config.getMinSyncReplicas());
    }
  }

  @Override
  public void close() throws Exception {
    // capture into a local before nulling the field, so a second close() call is a safe no-op
    // instead of an NPE.
    final ScheduledTask task = replicationCheckTask;
    replicationCheckTask = null;
    if (task != null) {
      task.cancel();
    }
  }

  /**
   * A queued position awaiting confirmation.
   *
   * @param position the exporter position
   * @param marker the confirmation value compared against {@link
   *     ReplicationSignalStrategy#computeConfirmedMarker}'s result (an LSN, a DB-clock-ms reading,
   *     or a delay-based release time)
   * @param enqueueTimeMs the JVM-clock instant when this entry was enqueued, used by {@link
   *     #queueHeadAge()}
   */
  record QueuedPosition(long position, long marker, long enqueueTimeMs) {}
}
