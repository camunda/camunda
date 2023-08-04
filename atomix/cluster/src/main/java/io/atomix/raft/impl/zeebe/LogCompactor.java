/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.impl.zeebe;

import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public final class LogCompactor {
  private final ThreadContext threadContext;
  private final RaftLog log;

  // Don't compact everything, leave enough entries so that slow followers are not forced into
  // snapshot replication immediately.
  private final int replicationThreshold;

  // hard coupled state
  private final Logger logger;
  private final RaftServiceMetrics metrics;

  // used when performing compaction; may be updated from a different thread
  private volatile long compactableIndex;

  public LogCompactor(
      final ThreadContext threadContext,
      final RaftLog log,
      final int replicationThreshold,
      final RaftServiceMetrics metrics,
      final Logger logger) {
    this.threadContext = threadContext;
    this.log = log;
    this.replicationThreshold = replicationThreshold;
    this.logger = logger;
    this.metrics = metrics;
  }

  public ThreadContext executor() {
    return threadContext;
  }

  /**
   * Assumes our snapshots are being taken asynchronously and we regularly update the compactable
   * index. Compaction is performed asynchronously.
   *
   * @return a future which is completed when the log has been compacted
   */
  public CompletableFuture<Void> compact() {
    threadContext.checkThread();

    final var index = compactableIndex - replicationThreshold;
    logger.debug("Compacting up to {} ({} - {})", index, compactableIndex, replicationThreshold);
    final CompletableFuture<Void> result = new CompletableFuture<>();
    try {
      final var startTime = System.currentTimeMillis();
      log.deleteUntil(index);
      metrics.compactionTime(System.currentTimeMillis() - startTime);
      result.complete(null);
    } catch (final Exception e) {
      logger.error("Failed to compact up to index {}", index, e);
      result.completeExceptionally(e);
    }

    return result;
  }

  public void setCompactableIndex(final long index) {
    compactableIndex = index;
  }

  /** Compacts the log based on the snapshot store's lowest compaction bound. */
  public void compactFromSnapshots(final PersistedSnapshotStore snapshotStore) {
    snapshotStore.getCompactionBound().onComplete(this::onSnapshotCompactionBound, threadContext);
  }

  private void onSnapshotCompactionBound(final Long index, final Throwable error) {
    if (error != null) {
      logger.error(
          "Expected to compact logs, but could not the compaction bound from the snapshot store",
          error);
      return;
    }

    setCompactableIndex(index);
    compact();
  }
}
