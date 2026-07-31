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
 * <p>Completing an already durable request ahead of pending earlier requests (see {@link
 * #flush(Journal, long)}) only reorders responses of re-transmitted appends, which the protocol
 * must tolerate anyway, as responses can be arbitrarily delayed or their requests re-sent after a
 * timeout.
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
      return CompletableFuture.failedFuture(closedError(null));
    }

    // fast path: the requested index is already durable, no flush is needed
    if (index <= journal.getLastFlushedIndex()) {
      return COMPLETED;
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
    drainPending(closedError(null));
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
      drainPending(closedError(e));
    }
  }

  private static FlushException closedError(final Throwable cause) {
    return new FlushException("Flusher is closed, cannot guarantee durability", cause);
  }

  private void flushNext(final Journal journal) {
    // must not hold the monitor while flushing, otherwise the Raft thread would block on new
    // flush requests for the whole flush duration
    try {
      completeRequests(journal, tryFlush(journal));
    } catch (final Throwable t) {
      // an Error, which tryFlush does not return, must not skip completing the pending results
      // either, as that would leave them - and all future requests - pending forever. It is not
      // swallowed: once the results are failed, it is rethrown so that the flush thread's uncaught
      // exception handler still sees it, e.g. to shut the JVM down on a VirtualMachineError.
      completeRequests(
          journal, new FlushException("Failed to flush journal, cannot guarantee durability", t));
      throw t;
    }
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
    // a closed or empty journal silently skips flushing, so it can never advance the flushed index
    // and cover the pending requests; fail them instead of rescheduling flushes forever. Pending
    // requests on an empty journal are rare but possible, e.g. an empty append acknowledging
    // records which were requested to be flushed before the log was reset.
    if (!journal.isOpen()) {
      return new FlushException("Journal is closed, cannot guarantee durability", null);
    }
    if (journal.isEmpty()) {
      return new FlushException("Journal is empty, cannot flush up to the requested index", null);
    }

    try {
      journal.flush();
      return null;
    } catch (final Exception e) {
      // any exception must be caught here, not just the expected journal exceptions: an escaping
      // exception would skip completing the pending results, leaving them - and all future
      // requests - pending forever. Errors are handled by the caller, which fails the pending
      // results before rethrowing them.
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
