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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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

  private final BlockingQueue<LsnPositionEntry> pendingEntries;
  private final AtomicLong flushedPosition = new AtomicLong(-1);
  private final AtomicLong replicatedPosition = new AtomicLong(-1);

  private final ScheduledTask replicationCheckTask;

  public LsnReplicationController(
      final Controller controller,
      final ReplicationLogStatusProvider lsnProvider,
      final ReplicationConfiguration replicationConfiguration,
      final int partitionId) {
    this.lsnProvider = lsnProvider;
    this.controller = controller;
    this.replicationConfiguration = replicationConfiguration;
    this.partitionId = partitionId;

    pendingEntries = new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    replicationCheckTask =
        controller.scheduleCancellableTask(
            replicationConfiguration.getPollingInterval(), this::checkReplication);
  }

  @Override
  public void onFlush(final long exporterPosition) {
    flushedPosition.set(exporterPosition);
    final long currentLsn = lsnProvider.getCurrent();
    if (!pendingEntries.offer(new LsnPositionEntry(exporterPosition, currentLsn))) {
      LOG.warn(
          "[RDBMS Exporter P{}] Replication queue is full, dropping LSN entry (lsn={}, position={})",
          partitionId,
          currentLsn,
          exporterPosition);
    }
  }

  private void checkReplication() {
    try {
      final long confirmedLsn = computeConfirmedLsn();

      LsnPositionEntry entry;
      long newReplicatedPosition = replicatedPosition.get();
      while ((entry = pendingEntries.peek()) != null && entry.lsn() <= confirmedLsn) {
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

  /**
   * Computes the maximum LSN confirmed by at least {@code minSyncReplicas} replicas. Sorts replica
   * statuses ascending and picks the Nth value — the highest LSN that at least N replicas have
   * reached. Returns {@link Long#MAX_VALUE} when minSyncReplicas is 0.
   */
  private long computeConfirmedLsn() {
    final int minSyncReplicas = replicationConfiguration.getMinSyncReplicas();
    if (minSyncReplicas == 0) {
      return Long.MAX_VALUE;
    }

    final var statuses = lsnProvider.getReplicationStatuses();
    if (statuses.size() < minSyncReplicas) {
      return -1;
    }

    return statuses.stream()
        .map(ReplicationStatusDto::getLogStatus)
        .sorted()
        .skip(minSyncReplicas - 1L)
        .findFirst()
        .orElse(-1L);
  }

  @Override
  public void close() throws Exception {
    replicationCheckTask.cancel();
  }

  int pendingEntriesSize() {
    return pendingEntries.size();
  }

  private record LsnPositionEntry(long position, long lsn) {}
}
