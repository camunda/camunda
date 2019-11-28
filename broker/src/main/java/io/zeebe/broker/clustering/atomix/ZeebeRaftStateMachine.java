/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix;

import io.atomix.protocols.raft.RaftStateMachine;
import io.atomix.protocols.raft.impl.RaftContext;
import io.atomix.protocols.raft.metrics.RaftServiceMetrics;
import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.storage.log.entry.RaftLogEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeebeRaftStateMachine implements RaftStateMachine {
  private final RaftContext raft;
  private final ThreadContext threadContext;
  private final ThreadContextFactory threadContextFactory;

  // hard coupled state
  private final RaftLogReader reader;
  private final Logger logger;
  private final ThreadContext compactionContext;
  private final RaftServiceMetrics metrics;

  // used when performing compaction; may be updated from a different thread
  private volatile long compactableIndex;

  // represents the last enqueued index
  private long lastEnqueued;

  public ZeebeRaftStateMachine(
      final RaftContext raft,
      final ThreadContext threadContext,
      final ThreadContextFactory threadContextFactory) {
    this.raft = raft;
    this.threadContext = threadContext;
    this.threadContextFactory = threadContextFactory;

    this.compactionContext = this.threadContextFactory.createContext();

    this.reader = raft.getLog().openReader(1, RaftLogReader.Mode.COMMITS);

    this.lastEnqueued = reader.getFirstIndex() - 1;
    this.logger = LoggerFactory.getLogger(this.getClass());
    this.metrics = new RaftServiceMetrics(raft.getName());
  }

  @Override
  public ThreadContext executor() {
    return threadContext;
  }

  /**
   * Assumes our snapshots are being taken asynchronously and we regularly update the compactable
   * index. Compaction is performed asynchronously.
   *
   * @return a future which is completed when the log has been compacted
   */
  @Override
  public CompletableFuture<Void> compact() {
    final var log = raft.getLog();
    if (log.isCompactable(compactableIndex)) {
      final var index = log.getCompactableIndex(compactableIndex);
      if (index > reader.getFirstIndex()) {
        final var future = new CompletableFuture<Void>();
        logger.debug("Compacting log up from {} up to {}", reader.getFirstIndex(), index);
        compactionContext.execute(() -> safeCompact(index, future));
        return future;
      }
    }

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void applyAll(final long index) {
    threadContext.execute(() -> safeApplyAll(index, null));
  }

  @Override
  public <T> CompletableFuture<T> apply(final long index) {
    final var future = new CompletableFuture<T>();
    threadContext.execute(() -> safeApplyAll(index, future));
    return future;
  }

  @Override
  public <T> CompletableFuture<T> apply(final Indexed<? extends RaftLogEntry> entry) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    threadContext.execute(() -> safeApplyIndexed(entry, future));
    return future;
  }

  @Override
  public void close() {
    compactionContext.close();
    reader.close();
  }

  @Override
  public void setCompactableIndex(final long index) {
    this.compactableIndex = index;
  }

  @Override
  public long getCompactableIndex() {
    return compactableIndex;
  }

  @Override
  public long getCompactableTerm() {
    throw new UnsupportedOperationException(
        "getCompactableTerm is not required by this implementation");
  }

  private void safeCompact(final long index, final CompletableFuture<Void> future) {
    compactionContext.checkThread();

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

  private void safeApplyAll(final long index, CompletableFuture<?> future) {
    threadContext.checkThread();

    lastEnqueued = Math.max(lastEnqueued, raft.getSnapshotStore().getCurrentSnapshotIndex());
    while (lastEnqueued < index) {
      final long nextIndex = ++lastEnqueued;
      threadContext.execute(() -> safeApplyIndex(nextIndex, future));
    }
  }

  private void safeApplyIndex(final long index, final CompletableFuture<?> future) {
    threadContext.checkThread();
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
        safeApplyIndexed(reader.next(), future);
      } catch (final Exception e) {
        logger.error("Failed to apply entry at index {}", index, e);
        optionalFuture.ifPresent(f -> f.completeExceptionally(e));
      }
    } else if (reader.getNextIndex() < raft.getSnapshotStore().getCurrentSnapshotIndex()) {
      reader.reset(raft.getSnapshotStore().getCurrentSnapshotIndex());
    } else {
      logger.error("Cannot apply index {}", index);
      logger.debug(
          "Expected next index is {} and reader hasNext {}",
          reader.getNextIndex(),
          reader.hasNext());
      optionalFuture.ifPresent(
          f ->
              f.completeExceptionally(
                  new IndexOutOfBoundsException("Cannot apply index " + index)));
    }
  }

  private <T> void safeApplyIndexed(
      final Indexed<? extends RaftLogEntry> indexed, final CompletableFuture<T> future) {
    threadContext.checkThread();
    final var optionalFuture = Optional.ofNullable(future);
    logger.trace("Applying {}", indexed);

    raft.notifyCommitListeners(indexed);
    optionalFuture.ifPresent(f -> f.complete(null));

    // mark as applied regardless of result
    raft.setLastApplied(indexed.index(), indexed.entry().term());
  }
}
