/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.io.InputStream;
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
 * disk. Create one limiter per broker and pass it to every backup store, so that all backups of the
 * broker draw from one budget.
 */
public final class BackupReadRateLimiter {

  private static final BackupReadRateLimiter UNLIMITED =
      new BackupReadRateLimiter(Long.MAX_VALUE, System::nanoTime, Sleeper.THREAD_SLEEP);

  private final long bytesPerSecond;
  private final LongSupplier nanoClock;
  private final Sleeper sleeper;
  private long nextFreeNanos;

  BackupReadRateLimiter(
      final long bytesPerSecond, final LongSupplier nanoClock, final Sleeper sleeper) {
    if (bytesPerSecond <= 0) {
      throw new IllegalArgumentException(
          "Expected backup read rate to be positive, but was %d bytes per second"
              .formatted(bytesPerSecond));
    }
    this.bytesPerSecond = bytesPerSecond;
    this.nanoClock = nanoClock;
    this.sleeper = sleeper;
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
   * @throws IllegalArgumentException if the rate is not positive
   */
  public static BackupReadRateLimiter of(final long bytesPerSecond) {
    return new BackupReadRateLimiter(bytesPerSecond, System::nanoTime, Sleeper.THREAD_SLEEP);
  }

  public boolean isUnlimited() {
    return this == UNLIMITED;
  }

  /** Returns a stream that pays for every byte read from or skipped in the given stream. */
  public RateLimitedInputStream wrap(final InputStream inputStream) {
    return new RateLimitedInputStream(inputStream, this);
  }

  /**
   * Pays for the given number of bytes.
   *
   * @return the nanoseconds the caller has to {@link #sleep(long) wait} until the reads before
   *     these bytes fit into the rate
   */
  long reserve(final long bytes) {
    if (isUnlimited() || bytes <= 0) {
      return 0;
    }

    final long costNanos = (long) ((double) bytes * TimeUnit.SECONDS.toNanos(1) / bytesPerSecond);
    synchronized (this) {
      final long now = nanoClock.getAsLong();
      final long start = Math.max(now, nextFreeNanos);
      nextFreeNanos = start + costNanos;
      return start - now;
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

  @FunctionalInterface
  interface Sleeper {
    Sleeper THREAD_SLEEP = TimeUnit.NANOSECONDS::sleep;

    void sleep(long nanos) throws InterruptedException;
  }
}
