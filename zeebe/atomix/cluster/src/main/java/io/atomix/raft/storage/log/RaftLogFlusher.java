/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.log;

import io.atomix.utils.concurrent.ThreadContextFactory;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.concurrent.CompletableFuture;

/**
 * Configurable flush strategy for the {@link io.atomix.raft.storage.log.RaftLog}. You can use its
 * implementations to improve performance at the cost of safety.
 *
 * <p>The default strategy is {@link DirectFlusher}, which is the safest but slowest option.
 *
 * <p>The {@link NoopFlusher} is the fastest but most dangerous option, as it will defer flushing to
 * the operating system. It's then possible to run into data corruption or data loss issues. Please
 * refer to the documentation regarding this.
 *
 * <p>{@link DelayedFlusher} can be configured to provide a trade-off between performance and
 * safety. This will cause flushes to be performed in a delayed fashion. See its documentation for
 * more. You should pick this if {@link DirectFlusher} does not provide the desired performance, but
 * you still wish a lower likelihood of corruption issues than with {@link NoopFlusher}. The
 * recommended configuration would be to find the smallest possible delay with which you achieve
 * your performance goals.
 */
public interface RaftLogFlusher extends CloseableSilently {

  /**
   * Signals that the journal should be durable at least up to the given index. The returned future
   * completes once this flusher's durability guarantee holds for the given index: implementations
   * which preserve Raft's durability contract complete it only after a flush covering the index
   * succeeded, while implementations which trade durability for performance (e.g. {@link
   * DelayedFlusher}, {@link NoopFlusher}) complete it immediately, before the data is actually on
   * disk.
   *
   * <p>Futures returned by consecutive calls complete in call order. They may complete on a
   * different thread than the calling thread, depending on the implementation.
   *
   * @param journal the journal to flush
   * @param index the index up to which durability is requested
   * @return a future which completes once records up to the given index may be treated as handled
   *     according to this flusher's guarantees; it fails if the flush failed, in which case the
   *     records must be assumed to not be durable
   */
  CompletableFuture<Void> flush(Journal journal, long index);

  /**
   * Signals that all records with an index greater than the given index ceased to exist, e.g.
   * because the log was truncated after a conflict, or reset when receiving a snapshot. Pending
   * flush results for such records must be failed instead of completed, as the records can never
   * become durable.
   *
   * <p>This is always called on the same thread which appends to and truncates the log, i.e. the
   * Raft thread.
   *
   * @param newLastIndex the highest index which is still part of the log
   */
  void onLogTruncation(long newLastIndex);

  @Override
  default void close() {}

  /**
   * An implementation of {@link RaftLogFlusher} which does nothing. When this is the configured
   * implementation, the journal is flushed only before a snapshot is taken.
   */
  final class NoopFlusher implements RaftLogFlusher {

    @Override
    public CompletableFuture<Void> flush(final Journal journal, final long index) {
      // trades durability for performance: callers may proceed immediately, the operating system
      // will eventually flush the journal
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onLogTruncation(final long newLastIndex) {
      // nothing to do - flush results complete immediately, so there are never pending results
    }
  }

  /**
   * An implementation of {@link RaftLogFlusher} which flushes immediately in a blocking fashion.
   * The returned future is always completed, and completed successfully only if the data written
   * before the call is guaranteed to be on disk.
   */
  final class DirectFlusher implements RaftLogFlusher {

    @Override
    public CompletableFuture<Void> flush(final Journal journal, final long index) {
      try {
        journal.flush();
        return CompletableFuture.completedFuture(null);
      } catch (final Exception e) {
        // any failure, not just the expected journal exceptions, means the records cannot be
        // treated as durable; callers turn this into a rejected append or a leader step-down
        return CompletableFuture.failedFuture(e);
      }
    }

    @Override
    public void onLogTruncation(final long newLastIndex) {
      // nothing to do - flushes are synchronous, so there are never pending flush results
    }
  }

  /**
   * Factory methods to create a new {@link RaftLogFlusher}. This is unfortunately required due to
   * the blackbox instantiation of the {@link io.atomix.raft.impl.RaftContext}.
   */
  @FunctionalInterface
  interface Factory {

    /** Shared, thread-safe, reusable {@link DirectFlusher} instance. */
    DirectFlusher DIRECT = new DirectFlusher();

    /** Shared, thread-safe, reusable {@link NoopFlusher} instance. */
    NoopFlusher NOOP = new NoopFlusher();

    /**
     * Creates a new {@link RaftLogFlusher} which should use the given thread context for
     * synchronization. If any {@link io.atomix.utils.concurrent.ThreadContext} are created, they
     * should be closed by the flusher.
     *
     * @param threadFactory the thread context factory for asynchronous operations
     * @return a configured Flusher
     */
    RaftLogFlusher createFlusher(final ThreadContextFactory threadFactory);

    /** Preset factory method which returns a shared {@link DirectFlusher} instance. */
    static DirectFlusher direct(final ThreadContextFactory ignored) {
      return DIRECT;
    }

    /** Preset factory method which returns a shared {@link NoopFlusher} instance. */
    static NoopFlusher noop(final ThreadContextFactory ignored) {
      return NOOP;
    }
  }
}
