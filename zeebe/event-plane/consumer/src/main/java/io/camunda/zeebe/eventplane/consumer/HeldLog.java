/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A log that a worker holds: a reader thread that polls it, and waits for a wake-up or the listing
 * interval when it has caught up. On release, the thread stops at an entry boundary and writes the
 * final cursor; after that, nobody writes this worker's cursor of the log again.
 */
final class HeldLog {

  private static final Logger LOG = LoggerFactory.getLogger(HeldLog.class);

  private final ObjectStore store;
  private final String cursorKey;
  private final String worker;
  private final LogReader reader;
  private final RecordHandler handler;
  private final long pollNanos;
  private final long retryNanos;
  private final Semaphore wakeUps = new Semaphore(0);
  private final Thread thread;
  private volatile boolean stopping;
  private volatile boolean halted;

  // Guarded by this: the last cursor written, and whether the final one is.
  private @Nullable Cursor written;
  private boolean released;

  HeldLog(
      final ObjectStore store,
      final String group,
      final String worker,
      final LogReader reader,
      final RecordHandler handler,
      final Duration pollInterval) {
    this.store = store;
    final var log = reader.log();
    cursorKey = ObjectNames.cursor(group, log.cluster(), log.tenant(), log.partition());
    this.worker = worker;
    this.reader = reader;
    this.handler = handler;
    pollNanos = pollInterval.toNanos();
    retryNanos = Math.min(pollNanos, Duration.ofSeconds(1).toNanos());
    thread = Thread.ofPlatform().daemon().name("event-plane-reader-" + log).unstarted(this::run);
  }

  LogId log() {
    return reader.log();
  }

  void start() {
    thread.start();
  }

  /** Makes the reader poll now, as after a notification of an upload. */
  void wake() {
    wakeUps.release();
  }

  /** Stops reading at the next entry boundary, and writes the final cursor. */
  void release() {
    stopping = true;
    reader.stop();
    wake();
  }

  /** Stops reading at the next entry boundary, without writing anything, as if killed. */
  void halt() {
    halted = true;
    release();
  }

  boolean isDone() {
    return !thread.isAlive();
  }

  void join(final Duration timeout) throws InterruptedException {
    thread.join(timeout);
  }

  /** Whether a cursor of this worker's was written since it took the log on. */
  synchronized boolean hasWritten() {
    return written != null;
  }

  /**
   * Writes the reader's cursor if it moved since the last write, unless the final one is written.
   *
   * @param last whether this is the final write, after which there is none
   */
  synchronized void writeCursor(final boolean last) throws IOException {
    if (released || halted) {
      return;
    }
    if (last) {
      released = true;
    }
    final var cursor = reader.cursor().orElse(null);
    if (cursor != null && !cursor.equals(written)) {
      store.put(cursorKey, new GroupCursor(cursor, worker).toJson());
      written = cursor;
    }
  }

  private void run() {
    while (!stopping) {
      long wait = pollNanos;
      try {
        if (reader.poll(handler)) {
          continue;
        }
      } catch (final VirtualMachineError e) {
        throw e;
      } catch (final Throwable e) {
        // A reader thread that died would leave the log held, and read by no one.
        LOG.warn("Failed to read log {}; trying again", reader.log(), e);
        wait = retryNanos;
      }
      try {
        if (wakeUps.tryAcquire(wait, TimeUnit.NANOSECONDS)) {
          wakeUps.drainPermits();
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (!halted) {
      try {
        writeCursor(true);
      } catch (final IOException e) {
        LOG.warn("Failed to write the final cursor of log {}", reader.log(), e);
      }
    }
  }
}
