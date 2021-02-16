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
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public final class LogCompactor {
  private final RaftContext raft;

  // hard coupled state
  private final Logger logger;
  private final RaftServiceMetrics metrics;

  // used when performing compaction; may be updated from a different thread
  private volatile long compactableIndex;

  public LogCompactor(final RaftContext raft) {
    this.raft = raft;

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

    final CompletableFuture<Void> result = new CompletableFuture<>();
    try {
      final var startTime = System.currentTimeMillis();
      raft.getLog().compact(compactableIndex);
      metrics.compactionTime(System.currentTimeMillis() - startTime);
      result.complete(null);
    } catch (final Exception e) {
      logger.error("Failed to compact up to index {}", compactableIndex, e);
      result.completeExceptionally(e);
    }

    return result;
  }

  public void close() {
    raft.checkThread();
    logger.debug("Closing the log compactor {}", raft.getName());
  }

  public void setCompactableIndex(final long index) {
    compactableIndex = index;
  }
}
