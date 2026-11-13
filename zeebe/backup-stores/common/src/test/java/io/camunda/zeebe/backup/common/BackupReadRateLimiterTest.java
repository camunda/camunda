/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BackupReadRateLimiterTest {

  private static final long MIB = 1024 * 1024;

  private final FakeClock clock = new FakeClock();

  @Test
  void shouldNeverWaitWhenUnlimited() throws IOException {
    // given
    final var limiter = BackupReadRateLimiter.unlimited();
    final var stream = limiter.wrap(new ZeroInputStream(100 * MIB));

    // when
    final var read = stream.transferTo(OutputStream.nullOutputStream());

    // then - the stream is not even wrapped, so nothing can wait
    assertThat(read).isEqualTo(100 * MIB);
    assertThat(stream).isInstanceOf(ZeroInputStream.class);
    assertThat(limiter.isUnlimited()).isTrue();
    assertThat(limiter).hasToString("BackupReadRateLimiter{unlimited}");
  }

  @Test
  void shouldWaitProportionallyToTheBytesRead() throws InterruptedException {
    // given
    final var limiter = new BackupReadRateLimiter(10 * MIB, clock::nanos, clock::sleep);

    // when - 30 MiB in chunks of 1 MiB; each chunk is paid for by the next read
    for (int i = 0; i < 30; i++) {
      limiter.acquire(MIB);
    }

    // then - the first chunk is free, the other 29 cost 100ms each
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(2_900));
  }

  @Test
  void shouldNotAccumulateCreditWhileIdle() throws InterruptedException {
    // given
    final var limiter = new BackupReadRateLimiter(10 * MIB, clock::nanos, clock::sleep);
    limiter.acquire(MIB);

    // when - idle for a minute, then read a burst
    clock.advance(Duration.ofMinutes(1));
    for (int i = 0; i < 10; i++) {
      limiter.acquire(MIB);
    }

    // then - the idle minute does not allow a burst above the limit
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(900));
  }

  @Test
  void shouldWaitForASingleReadLargerThanOneSecondOfBudget() throws InterruptedException {
    // given
    final var limiter = new BackupReadRateLimiter(MIB, clock::nanos, clock::sleep);

    // when
    limiter.acquire(5 * MIB);
    limiter.acquire(1);

    // then
    assertThat(clock.slept()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void shouldShareTheBudgetBetweenAllCallers() throws Exception {
    // given - two files are read concurrently through the same limiter
    final var limiter = new BackupReadRateLimiter(10 * MIB, clock::nanos, clock::sleep);
    final var first = limiter.wrap(new ZeroInputStream(10 * MIB));
    final var second = limiter.wrap(new ZeroInputStream(10 * MIB));
    final var buffer = new byte[(int) MIB];

    // when
    while (first.read(buffer) != -1 | second.read(buffer) != -1) {
      // interleave both readers
    }

    // then - 20 MiB at 10 MiB/s, minus the first free chunk
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(1_900));
  }

  @Test
  void shouldNotShareTheBudgetBetweenLimiters() throws InterruptedException {
    // given - two limiters of the same rate, as two brokers in one JVM would create them
    final var first = new BackupReadRateLimiter(10 * MIB, clock::nanos, clock::sleep);
    final var second = new BackupReadRateLimiter(10 * MIB, clock::nanos, clock::sleep);
    first.acquire(10 * MIB);

    // when
    second.acquire(10 * MIB);
    second.acquire(1);

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

  @Test
  void shouldRejectZeroRate() {
    // when - then
    assertThatThrownBy(() -> BackupReadRateLimiter.of(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void shouldRejectNegativeRate() {
    // when - then
    assertThatThrownBy(() -> BackupReadRateLimiter.of(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1");
  }
}
