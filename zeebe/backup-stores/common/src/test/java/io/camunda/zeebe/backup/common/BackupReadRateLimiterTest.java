/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import static io.camunda.zeebe.backup.common.BackupReadRateLimiter.MAX_READ_PAUSE;
import static io.camunda.zeebe.backup.common.BackupReadRateLimiter.MIN_BYTES_PER_SECOND;
import static io.camunda.zeebe.backup.common.BackupReadRateLimiter.UNBOUNDED_STREAMS;
import static io.camunda.zeebe.backup.common.RateLimitedInputStream.MAX_BYTES_PER_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BackupReadRateLimiterTest {

  private static final long MIB = 1024 * 1024;

  private final FakeClock clock = new FakeClock();

  @AfterEach
  void clearInterruptFlag() {
    // never leak an interrupt into the next test running on this thread
    Thread.interrupted();
  }

  @Test
  void shouldNeverWaitWhenUnlimited() throws IOException {
    // given
    final var limiter = BackupReadRateLimiter.unlimited();
    final var stream = limiter.wrap(new ZeroInputStream(100 * MIB));

    // when
    final var read = stream.transferTo(OutputStream.nullOutputStream());

    // then
    assertThat(read).isEqualTo(100 * MIB);
    assertThat(stream.waited()).isZero();
    assertThat(limiter.isUnlimited()).isTrue();
    assertThat(limiter).hasToString("BackupReadRateLimiter{unlimited}");
  }

  @Test
  void shouldWaitProportionallyToTheBytesRead() throws InterruptedException {
    // given
    final var limiter =
        new BackupReadRateLimiter(10 * MIB, UNBOUNDED_STREAMS, clock::nanos, clock::sleep);

    // when - 30 MiB in chunks of 1 MiB; each chunk is paid for by the next read
    for (int i = 0; i < 30; i++) {
      acquire(limiter, MIB);
    }

    // then - the first chunk is free, the other 29 cost 100ms each
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(2_900));
  }

  @Test
  void shouldNotAccumulateCreditWhileIdle() throws InterruptedException {
    // given
    final var limiter =
        new BackupReadRateLimiter(10 * MIB, UNBOUNDED_STREAMS, clock::nanos, clock::sleep);
    acquire(limiter, MIB);

    // when - idle for a minute, then read a burst
    clock.advance(Duration.ofMinutes(1));
    for (int i = 0; i < 10; i++) {
      acquire(limiter, MIB);
    }

    // then - the idle minute does not allow a burst above the limit
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(900));
  }

  @Test
  void shouldWaitForASingleReadLargerThanOneSecondOfBudget() throws InterruptedException {
    // given
    final var limiter =
        new BackupReadRateLimiter(MIB, UNBOUNDED_STREAMS, clock::nanos, clock::sleep);

    // when
    acquire(limiter, 5 * MIB);
    acquire(limiter, 1);

    // then
    assertThat(clock.slept()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void shouldShareTheBudgetBetweenAllCallers() throws Exception {
    // given - two files are read concurrently through the same limiter
    final var limiter =
        new BackupReadRateLimiter(10 * MIB, UNBOUNDED_STREAMS, clock::nanos, clock::sleep);
    final var first = limiter.wrap(new ZeroInputStream(10 * MIB));
    final var second = limiter.wrap(new ZeroInputStream(10 * MIB));
    final var buffer = new byte[(int) MIB];

    // when
    while (first.read(buffer) != -1 | second.read(buffer) != -1) {
      // interleave both readers
    }

    // then - 20 MiB at 10 MiB/s, minus the first free read of 64 KiB
    assertThat(clock.slept()).isEqualTo(Duration.ofSeconds(2).minus(Duration.ofNanos(6_250_000)));
  }

  @Test
  void shouldNotShareTheBudgetBetweenLimiters() throws InterruptedException {
    // given - two limiters of the same rate, as two brokers in one JVM would create them
    final var first =
        new BackupReadRateLimiter(10 * MIB, UNBOUNDED_STREAMS, clock::nanos, clock::sleep);
    final var second =
        new BackupReadRateLimiter(10 * MIB, UNBOUNDED_STREAMS, clock::nanos, clock::sleep);
    acquire(first, 10 * MIB);

    // when
    acquire(second, 10 * MIB);
    acquire(second, 1);

    // then - the second limiter only waits for its own read
    assertThat(clock.slept()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void shouldCreateLimitedLimiter() {
    // when
    final var limiter = BackupReadRateLimiter.of(42 * MIB);

    // then
    assertThat(limiter.isUnlimited()).isFalse();
    assertThat(limiter).hasToString("BackupReadRateLimiter{bytesPerSecond=44040192}");
  }

  /** Pays for the bytes and waits for them, like a read of a limited stream does. */
  private static void acquire(final BackupReadRateLimiter limiter, final long bytes)
      throws InterruptedException {
    limiter.sleep(limiter.reserve(bytes));
  }

  @Test
  void shouldRejectZeroRate() {
    // when - then
    assertThatThrownBy(
            () -> new BackupReadRateLimiter(0, UNBOUNDED_STREAMS, clock::nanos, clock::sleep))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void shouldRejectRateAtWhichASingleReadWaitsLongerThanTheMaxPause() {
    // when - then
    assertThatThrownBy(() -> BackupReadRateLimiter.of(MIN_BYTES_PER_SECOND - 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least " + MIN_BYTES_PER_SECOND);
    assertThat(BackupReadRateLimiter.of(MIN_BYTES_PER_SECOND).isUnlimited()).isFalse();
  }

  @Test
  void shouldLetOnlyAsManyStreamsOpenAsKeepEveryReadWithinTheMaxPause() {
    for (final long bytesPerSecond :
        new long[] {MIN_BYTES_PER_SECOND, 100_000, 1_000_000, 100_000_000, Long.MAX_VALUE}) {
      // when
      final int streams = BackupReadRateLimiter.maxOpenStreams(bytesPerSecond);

      // then - a read waits at most until one read of every open stream fits into the rate
      final double longestWaitSeconds = (double) streams * MAX_BYTES_PER_READ / bytesPerSecond;
      assertThat(streams).as("open streams at %d B/s", bytesPerSecond).isPositive();
      assertThat(longestWaitSeconds)
          .as("longest wait at %d B/s", bytesPerSecond)
          .isLessThanOrEqualTo(MAX_READ_PAUSE.toSeconds());
    }
  }

  @Test
  void shouldWaitToOpenAStreamUntilAnotherOneIsClosed() throws Exception {
    // given
    final var limiter = new BackupReadRateLimiter(MIB, 2, clock::nanos, clock::sleep);
    final var first = limiter.wrap(new ByteArrayInputStream(new byte[1]));
    limiter.wrap(new ByteArrayInputStream(new byte[1]));

    // when
    final var third = CompletableFuture.supplyAsync(() -> wrapUnchecked(limiter));

    // then
    assertThatThrownBy(() -> third.get(100, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
    first.close();
    assertThat(third).succeedsWithin(Duration.ofSeconds(30));
  }

  @Test
  void shouldLetOnlyOneStreamOpenWhenAStreamIsClosedTwice() throws Exception {
    // given
    final var limiter = new BackupReadRateLimiter(MIB, 1, clock::nanos, clock::sleep);
    final var first = limiter.wrap(new ByteArrayInputStream(new byte[1]));
    first.close();
    first.close();
    limiter.wrap(new ByteArrayInputStream(new byte[1]));

    // when
    final var third = CompletableFuture.supplyAsync(() -> wrapUnchecked(limiter));

    // then
    assertThatThrownBy(() -> third.get(100, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
    third.cancel(true);
  }

  @Test
  void shouldCloseTheGivenStreamWhenInterruptedWhileWaitingToOpen() throws Exception {
    // given
    final var limiter = new BackupReadRateLimiter(MIB, 1, clock::nanos, clock::sleep);
    limiter.wrap(new ByteArrayInputStream(new byte[1]));
    final var closed = new AtomicBoolean();
    final InputStream waiting =
        new ByteArrayInputStream(new byte[1]) {
          @Override
          public void close() {
            closed.set(true);
          }
        };

    // when
    Thread.currentThread().interrupt();

    // then
    assertThatThrownBy(() -> limiter.wrap(waiting)).isInstanceOf(InterruptedIOException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    assertThat(closed).isTrue();
  }

  @Test
  void shouldNotLimitTheOpenStreamsWhenUnlimited() throws IOException {
    // given
    final var limiter = BackupReadRateLimiter.unlimited();

    // when - then
    for (int i = 0; i < 10_000; i++) {
      limiter.wrap(new ByteArrayInputStream(new byte[1]));
    }
  }

  private static RateLimitedInputStream wrapUnchecked(final BackupReadRateLimiter limiter) {
    try {
      return limiter.wrap(new ByteArrayInputStream(new byte[1]));
    } catch (final InterruptedIOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void shouldRejectNegativeRate() {
    // when - then
    assertThatThrownBy(() -> BackupReadRateLimiter.of(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1");
  }
}
