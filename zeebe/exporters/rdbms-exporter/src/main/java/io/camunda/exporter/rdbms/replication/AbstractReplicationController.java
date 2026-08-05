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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared scaffolding for {@link ReplicationController} implementations that queue exporter
 * positions and confirm them once a subclass-specific condition proves they are safely replicated.
 * Every position flushed via {@link #enqueue(PendingEntry)} is kept in a bounded, debounced queue
 * until {@link #drainConfirmed(Predicate)} removes the entries a concrete subclass considers
 * confirmed; the highest confirmed position is then acknowledged to the {@link Controller}.
 * Subclasses implement {@link #doCheckReplication()}, run periodically by {@link
 * #checkReplication()}, to determine what "confirmed" and "in sync" mean for their replication
 * signal (log-sequence numbers, reported lag, ...).
 */
public abstract class AbstractReplicationController<
        E extends AbstractReplicationController.PendingEntry>
    implements ReplicationController {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final Controller controller;
  protected final ReplicationConfiguration config;
  protected final int partitionId;
  protected final InstantSource clock;
  protected final RdbmsWriterMetrics metrics;
  protected final AtomicLong flushedPosition = new AtomicLong(-1);
  protected final AtomicLong acknowledgedPosition = new AtomicLong(-1);

  private final BlockingQueue<E> pendingEntries;
  private final long queueDebounceMillis;
  private long lastAdded = Long.MIN_VALUE;
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private volatile ScheduledTask replicationCheckTask;

  protected AbstractReplicationController(
      final Controller controller,
      final ReplicationConfiguration config,
      final int partitionId,
      final InstantSource clock,
      final RdbmsWriterMetrics metrics) {
    this.controller = controller;
    this.config = config;
    this.partitionId = partitionId;
    this.clock = clock;
    this.metrics = metrics;

    queueDebounceMillis = config.getQueueDebounceTime().toMillis();
    pendingEntries = new ArrayBlockingQueue<>(config.getQueueCapacity());
    replicationCheckTask =
        controller.scheduleCancellableTask(config.getPollingInterval(), this::checkReplication);
  }

  @Override
  public final boolean isReplicationInSync() {
    return !paused.get();
  }

  protected final boolean isPaused() {
    return paused.get();
  }

  protected final void forcePause() {
    paused.set(true);
  }

  protected final boolean isQueueEmpty() {
    return pendingEntries.isEmpty();
  }

  protected final E peekPending() {
    return pendingEntries.peek();
  }

  protected final void recordFlushedPosition(final long position) {
    flushedPosition.set(position);
  }

  /**
   * Records the flushed position, captures a value from a fallible provider call, and enqueues an
   * entry built from it. On failure, force-pauses the exporter and logs instead of propagating.
   * Shared by every subclass's {@code onFlush}, which each need this same tag-then-enqueue-or-pause
   * sequence for one provider-sourced value (an LSN, a DB clock reading, ...).
   *
   * @param exporterPosition the flushed exporter position
   * @param providerCall the fallible call capturing the value to tag the entry with
   * @param entryFactory builds the queue entry from the captured value
   */
  protected final void onFlushCapturing(
      final long exporterPosition,
      final LongSupplier providerCall,
      final LongFunction<E> entryFactory) {
    recordFlushedPosition(exporterPosition);
    try {
      final long capturedValue = providerCall.getAsLong();
      log.debug(
          "[RDBMS Exporter P{}] Flushed position {}, captured replication marker {}, enqueueing "
              + "for replication check",
          partitionId,
          exporterPosition,
          capturedValue);
      enqueue(entryFactory.apply(capturedValue));
    } catch (final Exception e) {
      forcePause();
      log.error(
          "[RDBMS Exporter P{}] Failed to capture replication state after flushing exporter "
              + "position {}. Exporting will remain paused until replication checks recover.",
          partitionId,
          exporterPosition,
          e);
    }
  }

  /**
   * Queues the entry for later confirmation, coalescing it into the previous one if it was added
   * less than {@code queueDebounceTime} ago. Silently drops the entry if the queue is full - it's
   * fine, the next successfully queued entry will confirm the position anyway.
   */
  protected final void enqueue(final E entry) {
    final long now = clock.millis();

    if (queueDebounceMillis > 0
        && !pendingEntries.isEmpty()
        && now - lastAdded < queueDebounceMillis) {
      log.debug(
          "[RDBMS Exporter P{}] Debouncing flush (position={}), last added {} ms ago",
          partitionId,
          entry.position(),
          now - lastAdded);
      return;
    }

    if (!pendingEntries.offer(entry)) {
      log.warn(
          "[RDBMS Exporter P{}] Replication queue is full, dropping position entry (position={})",
          partitionId,
          entry.position());
    } else {
      lastAdded = now;
    }
  }

  /**
   * Removes every queued entry matching {@code isConfirmed} from the front of the queue.
   *
   * @return the highest-position entry removed, or {@code null} if nothing advanced past the
   *     currently acknowledged position
   */
  final E drainConfirmed(final Predicate<E> isConfirmed) {
    E lastConfirmedEntry = null;
    long newAcknowledgedPosition = acknowledgedPosition.get();
    E entry;
    while ((entry = pendingEntries.peek()) != null) {
      if (!isConfirmed.test(entry)) {
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

  /** Acknowledges the given entry as the new confirmed position. */
  protected final void acknowledge(
      final E entry, final Duration replicationLag, final int connectedReplicas) {
    acknowledgedPosition.set(entry.position());
    log.info(
        "[RDBMS Exporter P{}] Acknowledging position {} (replication lag: {}, replicas: {})",
        partitionId,
        entry.position(),
        replicationLag,
        connectedReplicas);
    controller.updateLastExportedRecordPosition(entry.position());
  }

  protected final void recordMetrics(final List<? extends ReplicationStatus> statuses) {
    metrics.recordReplicationStatus(
        statuses, isPaused(), flushedPosition.get(), acknowledgedPosition.get());
  }

  protected final void updatePausedState(
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

  @VisibleForTesting
  final void checkReplication() {
    try {
      doCheckReplication();
    } catch (final Exception e) {
      log.error(
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

  /**
   * Queries the replication signal, confirms queued positions that are now safe to acknowledge, and
   * updates the paused state accordingly.
   */
  protected abstract void doCheckReplication();

  @Override
  public final void close() throws Exception {
    // capture into a local before nulling the field, so a second close() call is a safe no-op
    // instead of an NPE - mirrors DelayReplicationController.close()'s existing idiom.
    final ScheduledTask task = replicationCheckTask;
    replicationCheckTask = null;
    if (task != null) {
      task.cancel();
    }
  }

  /** An entry linking an exporter position to the time it was flushed. */
  protected interface PendingEntry {
    long position();

    long enqueueTimeMs();
  }
}
