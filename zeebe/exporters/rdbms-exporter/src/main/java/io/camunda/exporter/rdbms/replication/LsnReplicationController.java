/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.db.rdbms.write.ReplicationLogStatusProvider;
import io.camunda.db.rdbms.write.ReplicationStatusDto;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LsnReplicationController implements ReplicationController {
  public static final int DEFAULT_QUEUE_CAPACITY = 8192;

  private static final Logger LOG = LoggerFactory.getLogger(LsnReplicationController.class);

  private final ReplicationLogStatusProvider lsnProvider;
  private final Controller controller;
  private final ReplicationConfiguration replicationConfiguration;
  private final int partitionId;
  private final InstantSource clock;

  private final BlockingQueue<LsnPositionEntry> pendingEntries;
  private final AtomicLong flushedPosition = new AtomicLong(-1);
  private final AtomicLong replicatedPosition = new AtomicLong(-1);
  private final AtomicBoolean paused = new AtomicBoolean(false);

  private final ScheduledTask replicationCheckTask;

  public LsnReplicationController(
      final Controller controller,
      final ReplicationLogStatusProvider lsnProvider,
      final ReplicationConfiguration replicationConfiguration,
      final int partitionId,
      final InstantSource clock) {
    this.lsnProvider = lsnProvider;
    this.controller = controller;
    this.replicationConfiguration = replicationConfiguration;
    this.partitionId = partitionId;
    this.clock = clock;

    pendingEntries = new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    replicationCheckTask =
        controller.scheduleCancellableTask(
            replicationConfiguration.getPollingInterval(), this::checkReplication);
  }

  @Override
  public void onFlush(final long exporterPosition) {
    flushedPosition.set(exporterPosition);
    final long currentLsn = lsnProvider.getCurrent();
    final long now = clock.millis();
    if (!pendingEntries.offer(new LsnPositionEntry(exporterPosition, currentLsn, now))) {
      // it's fine to just drop. When we are able to compact, when we export a new entry it will be
      // added.
      // if the queue is not full for too much it's not a problem, otherwise we will just hit the
      // max_lag bound.
      LOG.warn(
          "[RDBMS Exporter P{}] Replication queue is full, dropping LSN entry (lsn={}, position={})",
          partitionId,
          currentLsn,
          exporterPosition);
    }
  }

  @Override
  public boolean isPaused() {
    return paused.get();
  }

  private void checkReplication() {
    try {
      final int minSyncReplicas = replicationConfiguration.getMinSyncReplicas();
      final var statuses = lsnProvider.getReplicationStatuses();
      final int connectedReplicas = statuses.size();

      final long confirmedLsn = computeConfirmedLsn(statuses);
      final Duration maxLag = replicationConfiguration.getMaxLag();
      final Duration dbReportedLag = computeEffectiveLag(statuses, minSyncReplicas);
      final boolean dbLagExceeded = dbReportedLag.compareTo(maxLag) > 0;
      final boolean pauseOnMaxLag = replicationConfiguration.isPauseOnMaxLagExceeded();
      final boolean shouldPause = pauseOnMaxLag && dbLagExceeded;
      updatePausedState(shouldPause, dbReportedLag, maxLag, connectedReplicas, minSyncReplicas);
      final long now = clock.millis();
      final long lagCutoff = now - maxLag.toMillis();

      LsnPositionEntry entry;
      long newReplicatedPosition = replicatedPosition.get();
      while ((entry = pendingEntries.peek()) != null) {
        final String forcedReason;
        if (entry.lsn() <= confirmedLsn) {
          forcedReason = null;
        } else if (dbLagExceeded && !pauseOnMaxLag) {
          forcedReason =
              "DB-reported replication lag ("
                  + dbReportedLag
                  + ") exceeded maxLag ("
                  + maxLag
                  + ")";
        } else if (entry.enqueueTimeMs() <= lagCutoff) {
          forcedReason = "maxLag (" + maxLag + ") exceeded — replication has not caught up";
        } else {
          break;
        }

        if (forcedReason != null) {
          LOG.warn(
              "[RDBMS Exporter P{}] Confirming position {} (lsn={}) due to: {}",
              partitionId,
              entry.position(),
              entry.lsn(),
              forcedReason);
        }

        newReplicatedPosition = entry.position();
        pendingEntries.poll();
      }

      if (newReplicatedPosition > replicatedPosition.get()) {
        replicatedPosition.set(newReplicatedPosition);
        LOG.info(
            "[RDBMS Exporter P{}] Updating replicated position to {}",
            partitionId,
            newReplicatedPosition);
        controller.updateLastExportedRecordPosition(newReplicatedPosition);
      }
    } catch (final Exception e) {
      LOG.error(
          "[RDBMS Exporter P{}] Error while checking replication status, will retry after {}",
          partitionId,
          replicationConfiguration.getPollingInterval(),
          e);
    } finally {
      controller.scheduleCancellableTask(
          replicationConfiguration.getPollingInterval(), this::checkReplication);
    }
  }

  private void updatePausedState(
      final boolean shouldPause,
      final Duration dbReportedLag,
      final Duration maxLag,
      final int connectedReplicas,
      final int minSyncReplicas) {
    final boolean wasPaused = paused.getAndSet(shouldPause);
    if (shouldPause && !wasPaused) {
      LOG.warn(
          "[RDBMS Exporter P{}] Pausing exporter: DB-reported replication lag ({}) exceeded maxLag ({})",
          partitionId,
          dbReportedLag,
          maxLag);
    } else if (!shouldPause && wasPaused) {
      LOG.info(
          "[RDBMS Exporter P{}] Resuming exporter: replication quorum met ({}/{} replicas) and DB-reported lag ({}) within maxLag ({})",
          partitionId,
          connectedReplicas,
          minSyncReplicas,
          dbReportedLag,
          maxLag);
    }
  }

  /**
   * Computes the maximum LSN confirmed by at least {@code minSyncReplicas} replicas. Sorts replica
   * statuses ascending and picks the Nth value — the highest LSN that at least N replicas have
   * reached. Returns {@link Long#MAX_VALUE} when minSyncReplicas is 0 and the provider reports a
   * valid current LSN. Returns {@link Long#MIN_VALUE} when the provider signals LSN checking is not
   * available (i.e. current LSN is negative), so no entry can match via LSN comparison and entries
   * are only confirmed via the maxLag timeout.
   */
  @VisibleForTesting
  long computeConfirmedLsn() {
    return computeConfirmedLsn(lsnProvider.getReplicationStatuses());
  }

  private long computeConfirmedLsn(final List<ReplicationStatusDto> statuses) {
    if (lsnProvider.getCurrent() < 0) {
      return Long.MIN_VALUE;
    }

    final int minSyncReplicas = replicationConfiguration.getMinSyncReplicas();
    if (minSyncReplicas == 0) {
      return Long.MAX_VALUE;
    }

    if (statuses.size() < minSyncReplicas) {
      return -1;
    }

    return statuses.stream()
        .map(ReplicationStatusDto::logStatus)
        .sorted(Comparator.<Long>naturalOrder().reversed())
        .limit(minSyncReplicas)
        .min(Comparator.naturalOrder())
        .orElse(-1L);
  }

  /**
   * Computes the effective replication lag for quorum evaluation. Sorts per-replica lags ascending
   * and picks the Nth value — the worst lag among the best {@code minSyncReplicas} replicas, i.e.
   * the lag at which the quorum is still satisfied. Returns {@link Duration#ZERO} when the quorum
   * cannot be evaluated (minSyncReplicas is 0 or not enough replicas are connected) — quorum
   * violations are handled separately as a pause condition in {@link #checkReplication}.
   */
  @VisibleForTesting
  Duration computeEffectiveLag(
      final List<ReplicationStatusDto> statuses, final int minSyncReplicas) {
    if (minSyncReplicas <= 0 || statuses.size() < minSyncReplicas) {
      if (pendingEntries.isEmpty()) {
        return Duration.ZERO;
      }
      final long oldestLsnStatusLag = clock.millis() - pendingEntries.peek().enqueueTimeMs;
      return Duration.ofMillis(oldestLsnStatusLag);
    }

    final long quorumLagMs =
        statuses.stream()
            .mapToLong(ReplicationStatusDto::replicationLagMs)
            .sorted()
            .skip(minSyncReplicas - 1L)
            .findFirst()
            .orElse(0L);
    return Duration.ofMillis(quorumLagMs);
  }

  @Override
  public void close() throws Exception {
    replicationCheckTask.cancel();
  }

  int pendingEntriesSize() {
    return pendingEntries.size();
  }

  private record LsnPositionEntry(long position, long lsn, long enqueueTimeMs) {}
}
