/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.storage.log;

import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.Scheduler;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link RaftLogFlusher} which treats calls to {@link #flush(Journal)} as
 * signals that there is data to be flushed. When that happens, an asynchronous operation is
 * scheduled with a predefined delay. If a flush was already scheduled, then the signal is ignored.
 *
 * <p>In other words, this implementation flushes at least every given period, if there is anything
 * to flush.
 *
 * <p>A failed flush is not retried, as a retry would succeed without writing the data that failed
 * to be flushed. The journal records the failure and fails every later flush (see {@link
 * Journal#flush()}).
 *
 * <p>NOTE: this class is not thread safe, and is expected to run from the same thread as the
 * journal write path, e.g. the Raft thread.
 */
public final class DelayedFlusher implements RaftLogFlusher {

  private static final Logger LOGGER = LoggerFactory.getLogger(DelayedFlusher.class);
  private final Scheduler scheduler;
  private final Duration delayTime;

  private final Object scheduledMonitor = new Object();
  private Scheduled scheduledFlush;

  private boolean closed;
  // held while flushing, so that closing can wait for a running flush instead of interrupting it
  private final ReentrantLock flushLock = new ReentrantLock();

  public DelayedFlusher(final Scheduler scheduler, final Duration delayTime) {
    this.scheduler = Objects.requireNonNull(scheduler, "must specify a scheduler");
    this.delayTime = Objects.requireNonNull(delayTime, "must specify a valid flush delay");
  }

  @Override
  public void flush(final Journal journal) {
    scheduleFlush(journal);
  }

  @Override
  public void close() {
    synchronized (scheduledMonitor) {
      closed = true;

      if (scheduledFlush != null) {
        scheduledFlush.cancel();
        scheduledFlush = null;
      }
    }

    // Closing the scheduler interrupts a running flush, which closes the files it writes to
    // (ClosedByInterruptException): a healthy flush would fail, and the journal with it. Wait for
    // it first; a flush that starts afterwards sees that the flusher is closed and does nothing.
    flushLock.lock();
    try {
      LOGGER.trace("No delayed flush is running anymore, closing the scheduler");
    } finally {
      flushLock.unlock();
    }
    scheduler.close();
  }

  private void scheduleFlush(final Journal journal) {
    synchronized (scheduledMonitor) {
      if (closed) {
        LOGGER.debug("Skipped scheduling flush due to flusher being closed");
        return;
      }

      if (scheduledFlush == null) {
        LOGGER.trace(
            "Scheduling delayed flush in {} up to index {}", delayTime, journal.getLastIndex());
        scheduledFlush = scheduler.schedule(delayTime, () -> asyncFlush(journal));
      } else {
        LOGGER.trace("Skipped scheduling flush as there is already a pending, scheduled flush");
      }
    }
  }

  private void asyncFlush(final Journal journal) {
    flushLock.lock();
    try {
      synchronized (scheduledMonitor) {
        scheduledFlush = null;
        if (closed) {
          return;
        }
      }

      LOGGER.trace("Flushing journal after {}", delayTime);

      try {
        journal.flush();
      } catch (final FlushException | RuntimeException e) {
        LOGGER.error("Failed to flush journal, will not retry the flush", e);
      }
    } finally {
      flushLock.unlock();
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
        + '}';
  }
}
