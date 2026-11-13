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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RateLimitedInputStreamTest {

  private static final long BYTES_PER_SECOND = 1000;

  private final FakeClock clock = new FakeClock();
  private final BackupReadRateLimiter limiter =
      new BackupReadRateLimiter(BYTES_PER_SECOND, clock::nanos, clock::sleep);

  @AfterEach
  void clearInterruptFlag() {
    // never leak an interrupt into the next test running on this thread
    Thread.interrupted();
  }

  @Test
  void shouldReturnTheUnmodifiedContent() throws Exception {
    // given
    final var content = new byte[10_000];
    new Random(42).nextBytes(content);

    // when
    final byte[] read;
    try (final var stream = limiter.wrap(new ByteArrayInputStream(content))) {
      read = stream.readAllBytes();
    }

    // then
    assertThat(read).isEqualTo(content);
  }

  @Test
  void shouldOnlyChargeTheBytesActuallyRead() throws Exception {
    // given - a 100 byte stream read with a much larger buffer
    final var stream = limiter.wrap(new ByteArrayInputStream(new byte[100]));

    // when
    final int firstRead = stream.read(new byte[1_000_000]);
    readOneMoreByte();

    // then - the next read waits for the 100 bytes read, not for the buffer size
    assertThat(firstRead).isEqualTo(100);
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(100));
  }

  @Test
  void shouldChargeSingleByteReads() throws Exception {
    // given
    final var stream = limiter.wrap(new ByteArrayInputStream(new byte[11]));

    // when
    for (int i = 0; i < 11; i++) {
      assertThat(stream.read()).isZero();
    }

    // then - ten of the eleven bytes are paid for, 1ms each
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(10));
  }

  @Test
  void shouldNotChargeTheEndOfTheStream() throws Exception {
    // given
    final var stream = limiter.wrap(new ByteArrayInputStream(new byte[0]));

    // when
    final int read = stream.read(new byte[100]);
    final int singleRead = stream.read();
    final int nextRead = stream.read(new byte[100]);

    // then
    assertThat(read).isEqualTo(-1);
    assertThat(singleRead).isEqualTo(-1);
    assertThat(nextRead).isEqualTo(-1);
    assertThat(clock.slept()).isZero();
  }

  @Test
  void shouldChargeTheBytesActuallySkipped() throws Exception {
    // given
    final var stream = limiter.wrap(new ByteArrayInputStream(new byte[500]));

    // when
    final long skipped = stream.skip(10_000);
    readOneMoreByte();

    // then
    assertThat(skipped).isEqualTo(500);
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(500));
  }

  /** A read waits for the bytes charged before it, so one more read shows what was charged. */
  private void readOneMoreByte() throws IOException {
    limiter.wrap(new ByteArrayInputStream(new byte[1])).read();
  }

  @Test
  void shouldReportHowLongItsReadsWaited() throws Exception {
    // given - another stream uses the same limiter
    final var stream = limiter.wrap(new ByteArrayInputStream(new byte[300]));
    final var other = limiter.wrap(new ByteArrayInputStream(new byte[300]));

    // when - the first read of the limiter is free, each later one waits for the reads before it
    stream.read(new byte[100]);
    other.read(new byte[100]);
    stream.read(new byte[100]);

    // then - each stream reports its own waits, including those caused by the other stream
    assertThat(other.waited()).isEqualTo(Duration.ofMillis(100));
    assertThat(stream.waited()).isEqualTo(Duration.ofMillis(100));
    assertThat(clock.slept()).isEqualTo(Duration.ofMillis(200));
  }

  @Test
  void shouldReportAWaitAsSoonAsItStarts() throws Exception {
    // given - a limiter that lets the test look at the stream while a read is waiting
    final var waitedWhileWaiting = new AtomicReference<Duration>();
    final var stream = new AtomicReference<RateLimitedInputStream>();
    final var observingLimiter =
        new BackupReadRateLimiter(
            BYTES_PER_SECOND, clock::nanos, nanos -> waitedWhileWaiting.set(stream.get().waited()));
    stream.set(observingLimiter.wrap(new ByteArrayInputStream(new byte[200])));
    stream.get().read(new byte[100]);

    // when
    stream.get().read(new byte[100]);

    // then - a deadline that leaves out the waits must not expire in the middle of one
    assertThat(waitedWhileWaiting).hasValue(Duration.ofMillis(100));
  }

  @Test
  void shouldFailWithInterruptedIOExceptionWhenInterruptedWhileWaiting() throws Exception {
    // given
    final var interruptingLimiter =
        new BackupReadRateLimiter(
            BYTES_PER_SECOND,
            clock::nanos,
            nanos -> {
              throw new InterruptedException("interrupted while waiting");
            });
    final var stream = interruptingLimiter.wrap(new ByteArrayInputStream(new byte[10]));
    stream.read(new byte[5]);

    // when - then
    assertThatThrownBy(() -> stream.read(new byte[5]))
        .isInstanceOf(InterruptedIOException.class)
        .hasCauseInstanceOf(InterruptedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }
}
