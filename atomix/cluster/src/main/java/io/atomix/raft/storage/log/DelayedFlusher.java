/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.storage.log;

import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.Scheduler;
import io.camunda.zeebe.journal.Journal;
import java.time.Duration;
import java.util.Objects;

/**
 * An implementation of {@link RaftLogFlusher} which treats calls to {@link #flush(Journal)} as
 * signals that there is data to be flushed. When that happens, an asynchronous operation is
 * scheduled with a predefined delay. If a flush was already scheduled, then the signal is ignored.
 *
 * <p>In other words, this implementation flushes at least every given period, if there is anything
 * to flush.
 *
 * <p>NOTE: this class is not thread safe, and is expected to run from the same thread as the
 * journal write path, e.g. the Raft thread.
 */
public final class DelayedFlusher implements RaftLogFlusher {
  private final Scheduler scheduler;
  private final Duration delayTime;

  private Scheduled scheduledFlush;
  private boolean closed;

  public DelayedFlusher(final Scheduler scheduler, final Duration delayTime) {
    this.scheduler = Objects.requireNonNull(scheduler, "must specify a thread context");
    this.delayTime = Objects.requireNonNull(delayTime, "must specify a valid flush delay");
  }

  @Override
  public void flush(final Journal journal) {
    scheduleFlush(journal);
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }

    closed = true;
    if (scheduledFlush != null) {
      scheduledFlush.cancel();
    }
  }

  private void asyncFlush(final Journal journal) {
    scheduledFlush = null;

    if (closed || !journal.isOpen()) {
      return;
    }

    journal.flush();
  }

  private void scheduleFlush(final Journal journal) {
    if (scheduledFlush == null) {
      scheduledFlush = scheduler.schedule(delayTime, () -> asyncFlush(journal));
    }
  }

  @Override
  public String toString() {
    return "DelayedFlusher{"
        + "scheduler="
        + scheduler
        + ", delay="
        + delayTime
        + ", scheduledFlush="
        + scheduledFlush
        + ", closed="
        + closed
        + '}';
  }
}
