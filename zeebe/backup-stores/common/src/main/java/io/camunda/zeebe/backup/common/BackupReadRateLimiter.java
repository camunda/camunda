/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Limits how fast backup stores read local files, so that taking a backup cannot use up the disk
 * throughput that the journal needs to append and flush.
 *
 * <p>Every read pays for the bytes it returned by pushing back the earliest time at which the next
 * read may continue. Unused budget is not saved up, so after an idle period the limiter does not
 * allow a burst above the configured rate.
 *
 * <p>Backup stores are created per partition, but all partitions of a broker read from the same
 * disk. Use {@link #shared(long)} so that all backups of a broker draw from one budget.
 */
public final class BackupReadRateLimiter {

  public static final BackupReadRateLimiter UNLIMITED =
      new BackupReadRateLimiter(0, System::nanoTime, Sleeper.THREAD_SLEEP);

  private static final Map<Long, BackupReadRateLimiter> SHARED = new ConcurrentHashMap<>();

  private final long bytesPerSecond;
  private final LongSupplier nanoClock;
  private final Sleeper sleeper;
  private long nextFreeNanos;

  BackupReadRateLimiter(
      final long bytesPerSecond, final LongSupplier nanoClock, final Sleeper sleeper) {
    if (bytesPerSecond < 0) {
      throw new IllegalArgumentException(
          "Expected backup read rate to be 0 (unlimited) or positive, but was %d bytes per second"
              .formatted(bytesPerSecond));
    }
    this.bytesPerSecond = bytesPerSecond;
    this.nanoClock = nanoClock;
    this.sleeper = sleeper;
    nextFreeNanos = nanoClock.getAsLong();
  }

  /**
   * Returns the limiter for the given rate that is shared by all backups in this JVM.
   *
   * @param bytesPerSecond the maximum read rate; 0 means unlimited
   * @throws IllegalArgumentException if the rate is negative
   */
  public static BackupReadRateLimiter shared(final long bytesPerSecond) {
    if (bytesPerSecond == 0) {
      return UNLIMITED;
    }
    return SHARED.computeIfAbsent(
        bytesPerSecond,
        rate -> new BackupReadRateLimiter(rate, System::nanoTime, Sleeper.THREAD_SLEEP));
  }

  public long bytesPerSecond() {
    return bytesPerSecond;
  }

  public boolean isUnlimited() {
    return bytesPerSecond == 0;
  }

  /**
   * Pays for the given number of bytes, waiting until the reads before them fit into the rate.
   *
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public void acquire(final long bytes) throws InterruptedException {
    if (isUnlimited() || bytes <= 0) {
      return;
    }

    final long costNanos = (long) ((double) bytes * TimeUnit.SECONDS.toNanos(1) / bytesPerSecond);
    final long waitNanos;
    synchronized (this) {
      final long now = nanoClock.getAsLong();
      final long start = Math.max(now, nextFreeNanos);
      nextFreeNanos = start + costNanos;
      waitNanos = start - now;
    }

    if (waitNanos > 0) {
      sleeper.sleep(waitNanos);
    }
  }

  /** Returns a stream that pays for every byte read from or skipped in the given stream. */
  public InputStream wrap(final InputStream inputStream) {
    return isUnlimited() ? inputStream : new RateLimitedInputStream(inputStream, this);
  }

  @FunctionalInterface
  interface Sleeper {
    Sleeper THREAD_SLEEP = TimeUnit.NANOSECONDS::sleep;

    void sleep(long nanos) throws InterruptedException;
  }
}
