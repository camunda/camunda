/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.log;

import io.atomix.utils.concurrent.ThreadContext;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link RaftLogFlusher} which coalesces and dedupes flushes without giving up
 * durability. Like the {@link RaftLogFlusher.DirectFlusher}, flush results only complete once a
 * flush covering the requested index succeeded, so acknowledgements and commits gated on the
 * results keep their durability guarantee. Unlike it, flushing happens on a dedicated thread
 * context, off the Raft thread, and redundant flushes are elided:
 *
 * <ul>
 *   <li>a request for an index which is already durable completes immediately, without flushing;
 *       this covers e.g. commits below an index which an earlier flush already covered, and
 *       re-transmitted appends of already flushed records;
 *   <li>at most one flush runs at a time; requests arriving while one is in progress are all
 *       covered together by the next flush, as a single flush covers everything appended before it
 *       started (group commit).
 * </ul>
 *
 * <p>Results of requests which require flushing complete in request order. A request for an already
 * durable index completes immediately, possibly ahead of pending earlier requests; this only
 * reorders responses of re-transmitted appends, which the protocol must tolerate anyway, as
 * responses can be arbitrarily delayed or their requests re-sent after a timeout.
 *
 * <p>When a flush fails, all pending results are failed and no retry is attempted here: retries are
 * driven by the Raft protocol itself, i.e. followers respond with an error so that the leader
 * retries the append, and a leader steps down if it cannot commit.
 */
public final class CoalescedFlusher implements RaftLogFlusher {
  private static final Logger LOGGER = LoggerFactory.getLogger(CoalescedFlusher.class);

  private final ThreadContext flushContext;

  // waiters are queued in request order; as record indexes never decrease between consecutive
  // appends without a truncation in between - which fails all pending waiters - completing from
  // the head up to the flushed index preserves request order
  private final Deque<Waiter> pending = new ArrayDeque<>();

  // true while a flush is scheduled or running; used to guarantee a single in-flight flush
  private boolean flushing;
  private boolean closed;

  /**
   * @param flushContext the thread context on which flushes run; owned and closed by this flusher
   */
  public CoalescedFlusher(final ThreadContext flushContext) {
    this.flushContext = Objects.requireNonNull(flushContext, "must specify a thread context");
  }

  @Override
  public synchronized CompletableFuture<Void> flush(final Journal journal, final long index) {
    if (closed) {
      return CompletableFuture.failedFuture(
          new FlushException("Flusher is closed, cannot guarantee durability", null));
    }

    // fast path: the requested index is already durable, no flush is needed
    if (index <= journal.getLastFlushedIndex()) {
      return CompletableFuture.completedFuture(null);
    }

    final var waiter = new Waiter(index, new CompletableFuture<>());
    pending.add(waiter);
    scheduleFlush(journal);
    return waiter.result();
  }

  @Override
  public synchronized void onLogTruncation(final long newLastIndex) {
    if (pending.isEmpty()) {
      return;
    }

    LOGGER.debug(
        "Log truncated to index {}, failing {} pending flush result(s)",
        newLastIndex,
        pending.size());
    // fail all pending results, including those at or below the new last index: this keeps
    // completions of pending results strictly in request order, and the leader simply retries
    // the affected appends. Truncation only happens on leadership changes, so this is rare.
    drainPending(
        new FlushException(
            "Log was truncated to index %d, pending flush results are void".formatted(newLastIndex),
            null));
  }

  @Override
  public void close() {
    if (markClosed()) {
      flushContext.close();
    }
  }

  /**
   * Marks the flusher as closed and fails all pending results.
   *
   * @return true if this call closed the flusher, false if it was already closed
   */
  private synchronized boolean markClosed() {
    if (closed) {
      return false;
    }

    closed = true;
    drainPending(new FlushException("Flusher is closed, cannot guarantee durability", null));
    return true;
  }

  /** Must be called while holding the monitor, with at least one pending waiter. */
  private void scheduleFlush(final Journal journal) {
    if (flushing) {
      // the in-flight flush either covers the pending waiters, or reschedules once it finished
      return;
    }

    flushing = true;
    try {
      flushContext.execute(() -> flushNext(journal));
    } catch (final RejectedExecutionException e) {
      // only possible when the flush context is closing; fail the waiters instead of leaving them
      // pending forever
      flushing = false;
      LOGGER.debug("Failing pending flush results as the flusher is closing", e);
      drainPending(new FlushException("Flusher is closed, cannot guarantee durability", e));
    }
  }

  private void flushNext(final Journal journal) {
    // must not hold the monitor while flushing, otherwise the Raft thread would block on new
    // flush requests for the whole flush duration
    completeRequests(journal, tryFlush(journal));
  }

  private synchronized void completeRequests(final Journal journal, final Exception failure) {
    flushing = false;

    if (failure != null) {
      LOGGER.warn(
          "Failed to flush journal, failing {} pending flush result(s)", pending.size(), failure);
      drainPending(failure);
      return;
    }

    final long flushedIndex = journal.getLastFlushedIndex();
    while (!pending.isEmpty() && pending.peek().index() <= flushedIndex) {
      pending.poll().result().complete(null);
    }

    if (!pending.isEmpty() && !closed) {
      // records were appended and requested to be flushed while the flush was running; cover
      // them with a single follow-up flush
      scheduleFlush(journal);
    }
  }

  private Exception tryFlush(final Journal journal) {
    if (!journal.isOpen()) {
      return new FlushException("Journal is closed, cannot guarantee durability", null);
    }

    try {
      journal.flush();
      return null;
    } catch (final FlushException | JournalException | UncheckedIOException e) {
      return e;
    }
  }

  /** Must be called while holding the monitor. */
  private void drainPending(final Exception error) {
    Waiter waiter;
    while ((waiter = pending.poll()) != null) {
      waiter.result().completeExceptionally(error);
    }
  }

  private record Waiter(long index, CompletableFuture<Void> result) {}
}
