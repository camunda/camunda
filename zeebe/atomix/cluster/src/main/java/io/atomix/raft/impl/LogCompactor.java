/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.impl;

import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.VisibleForTesting;
import org.agrona.LangUtil;
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
  private volatile long compactionBound;
  private volatile long snapshotCompactionBound;

  public LogCompactor(
      final ThreadContext threadContext,
      final RaftLog log,
      final int replicationThreshold,
      final RaftServiceMetrics metrics,
      final Logger logger) {
    this.threadContext = threadContext;
    this.log = log;
    this.replicationThreshold = replicationThreshold;
    this.metrics = metrics;
    this.logger = logger;
  }

  @VisibleForTesting
  public long getCompactableIndex() {
    return Math.min(compactionBound, snapshotCompactionBound);
  }

  /**
   * Assumes our snapshots are being taken asynchronously, and we regularly update the compactable
   * index. It can happen that nothing is compacted (e.g. there are no snapshots since the last
   * compaction).
   *
   * <p>The log is compacted up to the latest compactable index, minus the configured replication
   * threshold. This is done in order to avoid replicating snapshots too much, which is often times
   * more expensive than replicating some entries.
   *
   * @return true if any data was deleted, false otherwise
   */
  public boolean compact() {
    return compact(getCompactableIndex() - replicationThreshold);
  }

  /**
   * Assumes our snapshots are being taken asynchronously, and we regularly update the compactable
   * index. It can happen that nothing is compacted (e.g. there are no snapshots since the last
   * compaction).
   *
   * <p>The log is compacted up to the latest compactable index, ignoring the replication threshold.
   *
   * @return true if any data was deleted, false otherwise
   */
  public boolean compactIgnoringReplicationThreshold() {
    return compact(getCompactableIndex());
  }

  /** Compacts the log based on the snapshot store's lowest compaction bound. */
  public void compactFromSnapshots(final PersistedSnapshotStore snapshotStore) {
    snapshotStore.getCompactionBound().onComplete(this::onSnapshotCompactionBound, threadContext);
  }

  private boolean compact(final long index) {
    threadContext.checkThread();

    try (final var ignored = metrics.compactionTime()) {
      final var compacted = log.deleteUntil(index);
      logger.debug("Compacted log up to index {}", index);
      return compacted;
    } catch (final Exception e) {
      logger.error("Failed to compact up to index {}", index, e);
      LangUtil.rethrowUnchecked(e);
      return false;
    }
  }

  private void onSnapshotCompactionBound(final Long index, final Throwable error) {
    if (error != null) {
      logger.error(
          "Expected to compact logs, but could not the compaction bound from the snapshot store",
          error);
      return;
    }

    logger.debug("Scheduling log compaction up to index {}", index);
    setSnapshotCompactionBound(index);
    compact();
  }

  public void setCompactionBound(final long compactionBound) {
    this.compactionBound = compactionBound;
  }

  public void setSnapshotCompactionBound(final long snapshotCompactionBound) {
    this.snapshotCompactionBound = snapshotCompactionBound;
  }
}
