/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.storage.log;

import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.util.CloseableSilently;

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
   * Signals that there is data to be flushed in the journal. The implementation may or may not
   * immediately flush this.
   *
   * @param journal the journal to flush
   */
  void flush(final Journal journal, final FlushMetaStore metaStore);

  /**
   * If this returns true, then any calls to {@link #flush(Journal, FlushMetaStore)} are synchronous
   * and immediate, and any guarantees offered by the implementation will hold after a call to
   * {@link #flush(Journal, FlushMetaStore)}.
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
    public void flush(final Journal ignoredJournal, final FlushMetaStore ignoredMetaStore) {}
  }

  /**
   * An implementation of {@link RaftLogFlusher} which flushes immediately in a blocking fashion.
   * After any calls to {@link #flush(Journal, FlushMetaStore)}, any data written before the call is
   * guaranteed to be on disk.
   */
  final class DirectFlusher implements RaftLogFlusher {

    @Override
    public void flush(final Journal journal, final FlushMetaStore metaStore) {
      journal.flush();
      metaStore.storeLastFlushedIndex(journal.getLastIndex());
    }

    @Override
    public boolean isDirect() {
      return true;
    }
  }

  /**
   * Temporary interface to allow the flusher to update the last flushed index once we know a flush
   * operation occurred. Ideally we will push this down into the journal, such that it becomes
   * unnecessary in a later iteration.
   */
  @FunctionalInterface
  interface FlushMetaStore {

    /**
     * Sets the last guaranteed flush index.
     *
     * @param lastIndex the last guaranteed flushed index
     */
    void storeLastFlushedIndex(final long lastIndex);
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
     * synchronization.
     *
     * @param flushContext the thread context for asynchronous operations
     * @return a configured Flusher
     */
    RaftLogFlusher createFlusher(final ThreadContext flushContext);

    /** Preset factory method which returns a shared {@link DirectFlusher} instance. */
    static DirectFlusher direct(final ThreadContext ignored) {
      return DIRECT;
    }

    /** Preset factory method which returns a shared {@link NoopFlusher} instance. */
    static NoopFlusher noop(final ThreadContext ignored) {
      return NOOP;
    }
  }
}
