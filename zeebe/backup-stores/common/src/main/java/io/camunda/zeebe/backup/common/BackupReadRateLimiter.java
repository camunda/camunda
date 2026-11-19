/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Limits how fast backup stores read local files, so that taking a backup cannot use up the disk
 * throughput that the journal needs to append and flush.
 *
 * <p>Every read pays for the bytes it returned by pushing back the earliest time at which the next
 * read may continue. Unused budget is not saved up, so after an idle period the limiter does not
 * allow a burst above the configured rate.
 *
 * <p>Backup stores are created per partition, but all partitions of a broker read from the same
 * disk. Create one limiter per broker and pass it to every backup store, so that all backups of the
 * broker draw from one budget.
 *
 * <p>A stream that waits for the limit does not read, so the upload it feeds does not write either.
 * HTTP clients abort an upload that writes nothing for too long, for example the AWS SDK after its
 * write timeout of 30 seconds by default. Each read pays for at most {@link
 * RateLimitedInputStream#MAX_BYTES_PER_READ} and waits at most for the reads that all other open
 * streams paid for before it, so the limiter only lets as many streams be open at once as keep this
 * pause within {@link #MAX_READ_PAUSE}. Further streams wait in {@link #wrap(InputStream)} until
 * one is closed; they have not started their upload yet, so no client counts that time against
 * them. A closed stream keeps its slot until the budget of its last read is used up, even when it
 * was interrupted before that read happened, as later reads still wait for that budget.
 */
public final class BackupReadRateLimiter {

  /**
   * The longest time a read of a limited stream waits for the limit. A third of the default write
   * timeout of the AWS SDK, so that an upload never stops writing for long enough to be aborted.
   */
  public static final Duration MAX_READ_PAUSE = Duration.ofSeconds(10);

  /** The lowest rate at which a single stream can still read without pausing longer. */
  public static final long MIN_BYTES_PER_SECOND =
      Math.ceilDiv(RateLimitedInputStream.MAX_BYTES_PER_READ, MAX_READ_PAUSE.toSeconds());

  /** Lets any number of streams be open at once. */
  static final int UNBOUNDED_STREAMS = Integer.MAX_VALUE;

  private static final BackupReadRateLimiter UNLIMITED =
      new BackupReadRateLimiter(
          Long.MAX_VALUE,
          UNBOUNDED_STREAMS,
          System::nanoTime,
          Sleeper.THREAD_SLEEP,
          Scheduler.DELAYED);

  private final long bytesPerSecond;
  private final LongSupplier nanoClock;
  private final Sleeper sleeper;
  private final Scheduler scheduler;
  private final @Nullable Semaphore openStreams;
  private long nextFreeNanos;

  BackupReadRateLimiter(
      final long bytesPerSecond,
      final int maxOpenStreams,
      final LongSupplier nanoClock,
      final Sleeper sleeper,
      final Scheduler scheduler) {
    if (bytesPerSecond <= 0) {
      throw new IllegalArgumentException(
          "Expected backup read rate to be positive, but was %d bytes per second"
              .formatted(bytesPerSecond));
    }
    if (maxOpenStreams <= 0) {
      throw new IllegalArgumentException(
          "Expected the number of open streams to be positive, but was %d"
              .formatted(maxOpenStreams));
    }
    this.bytesPerSecond = bytesPerSecond;
    this.nanoClock = nanoClock;
    this.sleeper = sleeper;
    this.scheduler = scheduler;
    openStreams = maxOpenStreams == UNBOUNDED_STREAMS ? null : new Semaphore(maxOpenStreams, true);
    nextFreeNanos = nanoClock.getAsLong();
  }

  /** Returns a limiter that never makes a read wait. */
  public static BackupReadRateLimiter unlimited() {
    return UNLIMITED;
  }

  /**
   * Returns a new limiter with its own budget. Backups only share the budget when they are given
   * the same instance.
   *
   * @param bytesPerSecond the maximum read rate
   * @throws IllegalArgumentException if the rate is below {@link #MIN_BYTES_PER_SECOND}
   */
  public static BackupReadRateLimiter of(final long bytesPerSecond) {
    if (bytesPerSecond < MIN_BYTES_PER_SECOND) {
      throw new IllegalArgumentException(
          "Expected backup read rate to be at least %d bytes per second, so that a read never waits longer than %s, but was %d bytes per second"
              .formatted(MIN_BYTES_PER_SECOND, MAX_READ_PAUSE, bytesPerSecond));
    }
    return new BackupReadRateLimiter(
        bytesPerSecond,
        maxOpenStreams(bytesPerSecond),
        System::nanoTime,
        Sleeper.THREAD_SLEEP,
        Scheduler.DELAYED);
  }

  /**
   * Returns how many streams can be open at once without a read waiting longer than {@link
   * #MAX_READ_PAUSE}: a read waits at most until one read of every open stream fits into the rate.
   */
  static int maxOpenStreams(final long bytesPerSecond) {
    final double streams =
        (double) bytesPerSecond
            * MAX_READ_PAUSE.toSeconds()
            / RateLimitedInputStream.MAX_BYTES_PER_READ;
    return (int) Math.max(1, Math.min(UNBOUNDED_STREAMS - 1, streams));
  }

  public boolean isUnlimited() {
    return this == UNLIMITED;
  }

  /**
   * Returns a stream that pays for every byte read from or skipped in the given stream. Waits until
   * fewer streams of this limiter are open than it can serve, see the class documentation. Close
   * the returned stream to let the next one open.
   *
   * @throws InterruptedIOException if interrupted while waiting; the given stream is closed then
   */
  public RateLimitedInputStream wrap(final InputStream inputStream) throws InterruptedIOException {
    if (openStreams != null) {
      try {
        openStreams.acquire();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        closeQuietly(inputStream);
        final var exception =
            new InterruptedIOException("Interrupted while waiting to open a limited backup read");
        exception.initCause(e);
        throw exception;
      }
    }
    return new RateLimitedInputStream(inputStream, this);
  }

  /**
   * Lets the next stream open; called once by every stream returned by {@link #wrap}.
   *
   * @param lastRead the last read the stream paid for, or {@code null} if it never read
   */
  void streamClosed(final @Nullable Reservation lastRead) {
    if (openStreams == null) {
      return;
    }
    if (lastRead == null || lastRead == Reservation.FREE) {
      openStreams.release();
      return;
    }
    final long remainingNanos = lastRead.endNanos() - nanoClock.getAsLong();
    if (remainingNanos > 0) {
      scheduler.schedule(remainingNanos, openStreams::release);
    } else {
      openStreams.release();
    }
  }

  private static void closeQuietly(final InputStream inputStream) {
    try {
      inputStream.close();
    } catch (final IOException e) {
      // the caller is interrupted and gets an exception about that; it never used this stream
    }
  }

  /**
   * Pays for the given number of bytes.
   *
   * @return how long the caller has to {@link #sleep(long) wait} until the reads before these bytes
   *     fit into the rate, and when the budget these bytes take is used up
   */
  Reservation reserve(final long bytes) {
    if (isUnlimited() || bytes <= 0) {
      return Reservation.FREE;
    }

    final long costNanos = (long) ((double) bytes * TimeUnit.SECONDS.toNanos(1) / bytesPerSecond);
    synchronized (this) {
      final long now = nanoClock.getAsLong();
      // nanoTime values may overflow, so only their difference is meaningful
      final long start = nextFreeNanos - now > 0 ? nextFreeNanos : now;
      nextFreeNanos = start + costNanos;
      return new Reservation(start - now, start + costNanos);
    }
  }

  void sleep(final long nanos) throws InterruptedException {
    sleeper.sleep(nanos);
  }

  @Override
  public String toString() {
    return isUnlimited()
        ? "BackupReadRateLimiter{unlimited}"
        : "BackupReadRateLimiter{bytesPerSecond=" + bytesPerSecond + '}';
  }

  /**
   * A paid read.
   *
   * @param waitNanos how long the read has to wait until the reads before it fit into the rate
   * @param endNanos when the budget of the read is used up, on the limiter's clock; only meaningful
   *     when compared to that clock by subtraction, as {@link System#nanoTime()} values are
   */
  record Reservation(long waitNanos, long endNanos) {
    /** A read that took no budget, so it has no end; never compare its {@code endNanos}. */
    static final Reservation FREE = new Reservation(0, 0);
  }

  @FunctionalInterface
  interface Sleeper {
    Sleeper THREAD_SLEEP = TimeUnit.NANOSECONDS::sleep;

    void sleep(long nanos) throws InterruptedException;
  }

  @FunctionalInterface
  interface Scheduler {
    /**
     * Runs the task on the delay thread itself rather than the common pool, which closing backup
     * stores can block for a minute. Only use it for short tasks that never block.
     */
    Scheduler DELAYED =
        (delayNanos, task) ->
            CompletableFuture.delayedExecutor(delayNanos, TimeUnit.NANOSECONDS, Runnable::run)
                .execute(task);

    void schedule(long delayNanos, Runnable task);
  }
}
