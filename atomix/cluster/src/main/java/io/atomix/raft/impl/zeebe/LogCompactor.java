/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.atomix.raft.impl.zeebe;

import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class LogCompactor {
  private final RaftContext raft;

  // hard coupled state
  private final RaftLogReader reader;
  private final Logger logger;
  private final RaftServiceMetrics metrics;

  // used when performing compaction; may be updated from a different thread
  private volatile long compactableIndex;

  public LogCompactor(final RaftContext raft) {
    this.raft = raft;
    reader = raft.getLog().openReader(1, RaftLogReader.Mode.COMMITS);

    logger =
        ContextualLoggerFactory.getLogger(
            getClass(), LoggerContext.builder(getClass()).addValue(raft.getName()).build());
    metrics = new RaftServiceMetrics(raft.getName());
  }

  public ThreadContext executor() {
    return raft.getThreadContext();
  }

  /**
   * Assumes our snapshots are being taken asynchronously and we regularly update the compactable
   * index. Compaction is performed asynchronously.
   *
   * @return a future which is completed when the log has been compacted
   */
  public CompletableFuture<Void> compact() {
    raft.checkThread();

    final var log = raft.getLog();
    if (log.isCompactable(compactableIndex)) {
      final var index = log.getCompactableIndex(compactableIndex);
      final var future = new CompletableFuture<Void>();
      logger.debug("Compacting log up from {} up to {}", reader.getFirstIndex(), index);
      compact(index, future);
      return future;
    } else {
      logger.debug(
          "Skipping compaction of non-compactable index {} (first log index: {})",
          compactableIndex,
          reader.getFirstIndex());
    }

    return CompletableFuture.completedFuture(null);
  }

  public void close() {
    raft.checkThread();
    logger.debug("Closing the log compactor {}", raft.getName());
    reader.close();
  }

  public void setCompactableIndex(final long index) {
    compactableIndex = index;
  }

  private void compact(final long index, final CompletableFuture<Void> future) {
    try {
      final var startTime = System.currentTimeMillis();
      raft.getLog().compact(index);
      metrics.compactionTime(System.currentTimeMillis() - startTime);
      future.complete(null);
    } catch (final Exception e) {
      logger.error("Failed to compact up to index {}", index, e);
      future.completeExceptionally(e);
    }
  }
}
