/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix;

import io.atomix.raft.RaftStateMachine;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public final class ZeebeRaftStateMachine implements RaftStateMachine {
  private final RaftContext raft;

  // hard coupled state
  private final RaftLogReader reader;
  private final Logger logger;
  private final RaftServiceMetrics metrics;

  // used when performing compaction; may be updated from a different thread
  private volatile long compactableIndex;

  // represents the last enqueued index
  private long lastEnqueued;

  public ZeebeRaftStateMachine(final RaftContext raft) {
    this.raft = raft;
    this.reader = raft.getLog().openReader(1, RaftLogReader.Mode.COMMITS);

    this.lastEnqueued = reader.getFirstIndex() - 1;
    this.logger =
        ContextualLoggerFactory.getLogger(
            getClass(), LoggerContext.builder(getClass()).add("partition", raft.getName()).build());
    this.metrics = new RaftServiceMetrics(raft.getName());
  }

  @Override
  public ThreadContext executor() {
    return raft.getThreadContext();
  }

  /**
   * Assumes our snapshots are being taken asynchronously and we regularly update the compactable
   * index. Compaction is performed asynchronously.
   *
   * @return a future which is completed when the log has been compacted
   */
  @Override
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

  @Override
  public void applyAll(final long index) {
    raft.checkThread();
    applyAll(index, null);
  }

  @Override
  public <T> CompletableFuture<T> apply(final long index) {
    raft.checkThread();
    final var future = new CompletableFuture<T>();
    applyAll(index, future);
    return future;
  }

  @Override
  public <T> CompletableFuture<T> apply(final Indexed<? extends RaftLogEntry> entry) {
    raft.checkThread();
    final CompletableFuture<T> future = new CompletableFuture<>();
    applyIndexed(entry, future);
    return future;
  }

  @Override
  public void close() {
    raft.checkThread();
    logger.debug("Closing state machine {}", raft.getName());
    reader.close();
  }

  @Override
  public long getCompactableIndex() {
    return compactableIndex;
  }

  @Override
  public void setCompactableIndex(final long index) {
    this.compactableIndex = index;
  }

  @Override
  public long getCompactableTerm() {
    throw new UnsupportedOperationException(
        "getCompactableTerm is not required by this implementation");
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

  private void applyAll(final long index, final CompletableFuture<?> future) {
    lastEnqueued = Math.max(lastEnqueued, raft.getSnapshotStore().getCurrentSnapshotIndex());
    while (lastEnqueued < index) {
      final long nextIndex = ++lastEnqueued;
      applyIndex(nextIndex, future);
    }
  }

  private void applyIndex(final long index, final CompletableFuture<?> future) {
    final var optionalFuture = Optional.ofNullable(future);

    // skip if we have a newer snapshot
    if (raft.getSnapshotStore().getCurrentSnapshotIndex() >= index
        || reader.getNextIndex() > index) {
      optionalFuture.ifPresent(f -> f.complete(null));
      return;
    }

    if (index > reader.getNextIndex()) {
      reader.reset(index);
    }

    // Apply entries prior to this entry.
    if (reader.hasNext() && reader.getNextIndex() == index) {
      try {
        applyIndexed(reader.next(), future);
      } catch (final Exception e) {
        logger.error("Failed to apply entry at index {}", index, e);
        optionalFuture.ifPresent(f -> f.completeExceptionally(e));
      }
    } else if (reader.getNextIndex() < raft.getSnapshotStore().getCurrentSnapshotIndex()) {
      reader.reset(raft.getSnapshotStore().getCurrentSnapshotIndex());
    } else {
      // in the case where we tried to apply indexes out of order
      logger.error(
          "Cannot apply index {}, expected next index is {} (has more entries: {})",
          index,
          reader.getNextIndex(),
          reader.hasNext());
      optionalFuture.ifPresent(
          f ->
              f.completeExceptionally(
                  new IndexOutOfBoundsException("Cannot apply index " + index)));
    }
  }

  private <T> void applyIndexed(
      final Indexed<? extends RaftLogEntry> indexed, final CompletableFuture<T> future) {
    final var optionalFuture = Optional.ofNullable(future);
    logger.trace("Applying {}", indexed);

    raft.notifyCommitListeners(indexed);
    optionalFuture.ifPresent(f -> f.complete(null));

    // mark as applied regardless of result
    raft.setLastApplied(indexed.index(), indexed.entry().term());
  }
}
