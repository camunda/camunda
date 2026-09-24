/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.log;

import io.atomix.utils.concurrent.ThreadContextFactory;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
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
@FunctionalInterface
public interface RaftLogFlusher extends CloseableSilently {

  /**
   * Signals that the journal should be flushed at least up to the given index. The implementation
   * may or may not immediately flush this.
   *
   * @param journal the journal to flush
   * @param index the index up to which the journal should be flushed
   * @return a future which completes once the implementation's guarantees hold for the given index,
   *     or fails if the flush failed
   */
  CompletableFuture<Void> flush(final Journal journal, final long index);

  /**
   * If this returns true, then any calls to {@link #flush(Journal, long)} are synchronous and
   * immediate, and any guarantees offered by the implementation will hold after a call to {@link
   * #flush(Journal, long)}.
   */
  default boolean isDirect() {
    return false;
  }

  @Override
  default void close() {}

  /**
   * An implementation of {@link RaftLogFlusher} which does nothing. When this is the configured
   * implementation, the journal is flushed only before a snapshot is taken.
   */
  final class NoopFlusher implements RaftLogFlusher {

    @Override
    public CompletableFuture<Void> flush(final Journal ignoredJournal, final long ignoredIndex) {
      return CompletableFuture.completedFuture(null);
    }
  }

  /**
   * An implementation of {@link RaftLogFlusher} which flushes immediately in a blocking fashion.
   * Once the future returned by {@link #flush(Journal, long)} completed successfully, any data
   * written before the call is guaranteed to be on disk. The future is always completed when the
   * call returns.
   */
  final class DirectFlusher implements RaftLogFlusher {

    @Override
    public CompletableFuture<Void> flush(final Journal journal, final long ignoredIndex) {
      try {
        journal.flush();
        return CompletableFuture.completedFuture(null);
      } catch (final FlushException e) {
        return CompletableFuture.failedFuture(e);
      }
    }

    @Override
    public boolean isDirect() {
      return true;
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
