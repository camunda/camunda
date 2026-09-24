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
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An implementation of {@link RaftLogFlusher} with the same guarantee as the {@link
 * RaftLogFlusher.DirectFlusher}: a flush result only completes once a flush covering the requested
 * index succeeded. Unlike it, it skips redundant flushes and flushes on its own thread:
 *
 * <ul>
 *   <li>a request for an index which is already flushed completes immediately;
 *   <li>at most one flush runs at a time, and all requests which arrive while it runs are covered
 *       together by the next one.
 * </ul>
 *
 * <p>A flush result for an already flushed index may complete before the results of earlier
 * requests. This only reorders responses, which the Raft protocol tolerates anyway.
 *
 * <p>A failed flush fails all pending results, without retrying: the Raft protocol retries on its
 * own, as followers reject the append and leaders step down.
 */
@NullMarked
public final class CoalescedFlusher implements RaftLogFlusher {
  private final ThreadContext flushContext;

  // Requests are queued in call order. The requested indexes do not decrease between truncations,
  // and a truncation fails all pending requests, so completing them from the head up to the flushed
  // index preserves the order.
  private final Queue<Request> pending = new ArrayDeque<>();
  private boolean flushing;
  private boolean closed;

  /**
   * @param flushContext the thread context on which flushes run; closed when this flusher is
   *     closed. It must not share a thread with the Raft thread, as a leader blocks the Raft thread
   *     until its flush completed.
   */
  public CoalescedFlusher(final ThreadContext flushContext) {
    this.flushContext = Objects.requireNonNull(flushContext, "must specify a thread context");
  }

  @Override
  public synchronized CompletableFuture<Void> flush(final Journal journal, final long index) {
    if (closed) {
      return CompletableFuture.failedFuture(new FlushException("Flusher is closed", null));
    }

    if (index <= journal.getLastFlushedIndex()) {
      return CompletableFuture.completedFuture(null);
    }

    final var request = new Request(index, new CompletableFuture<>());
    pending.add(request);
    if (!flushing) {
      flushing = true;
      flushContext.execute(() -> flushPending(journal));
    }

    return request.result();
  }

  /**
   * Fails all pending results, as the records they are waiting for may not exist anymore. Failing
   * all of them, and not only those above the new last index, keeps results completing in order.
   */
  @Override
  public synchronized void onLogTruncation(final long newLastIndex) {
    failPending(
        new FlushException(
            "Log was truncated after index %d while flushing".formatted(newLastIndex), null));
  }

  @Override
  public void close() {
    synchronized (this) {
      closed = true;
      failPending(new FlushException("Flusher is closed", null));
    }

    flushContext.close();
  }

  private void flushPending(final Journal journal) {
    try {
      flushJournal(journal);
      completePending(journal, null);
    } catch (final FlushException e) {
      completePending(journal, e);
    } catch (final Throwable e) {
      // any failure must fail the pending requests, or they would never complete; errors are
      // rethrown for the thread's uncaught exception handler, e.g. to exit on a VirtualMachineError
      completePending(journal, new FlushException("Failed to flush journal", e));
      if (e instanceof final Error error) {
        throw error;
      }
    }
  }

  private static void flushJournal(final Journal journal) throws FlushException {
    // the journal skips flushing while it is closed or empty, so it would never cover the pending
    // requests
    if (!journal.isOpen() || journal.isEmpty()) {
      throw new FlushException("Journal is closed or empty", null);
    }

    journal.flush();
  }

  private synchronized void completePending(
      final Journal journal, final @Nullable FlushException failure) {
    if (failure != null) {
      failPending(failure);
    }

    final long flushedIndex = journal.getLastFlushedIndex();
    while (!pending.isEmpty() && pending.peek().index() <= flushedIndex) {
      pending.poll().result().complete(null);
    }

    // cover the requests which arrived while flushing with a single, follow-up flush
    flushing = !pending.isEmpty() && !closed;
    if (flushing) {
      flushContext.execute(() -> flushPending(journal));
    }
  }

  private void failPending(final FlushException failure) {
    Request request;
    while ((request = pending.poll()) != null) {
      request.result().completeExceptionally(failure);
    }
  }

  private record Request(long index, CompletableFuture<Void> result) {}
}
