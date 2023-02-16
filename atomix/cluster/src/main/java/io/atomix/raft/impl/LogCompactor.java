/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.impl;

import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
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
    this.metrics = metrics;
    this.logger = logger;
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
    return compact(compactableIndex - replicationThreshold);
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
    return compact(compactableIndex);
  }

  private boolean compact(final long index) {
    threadContext.checkThread();

    try {
      final var startTime = System.currentTimeMillis();
      final var compacted = log.deleteUntil(index);

      metrics.compactionTime(System.currentTimeMillis() - startTime);
      logger.debug("Compacted log up to index {}", index);
      return compacted;
    } catch (final Exception e) {
      logger.error("Failed to compact up to index {}", index, e);
      LangUtil.rethrowUnchecked(e);
      return false;
    }
  }

  public void setCompactableIndex(final long index) {
    compactableIndex = index;
  }
}
