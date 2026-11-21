/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

final class ThrottledLoggerTest {

  private final AtomicLong now = new AtomicLong(1_000_000L);
  private final InstantSource clock = () -> Instant.ofEpochMilli(now.get());

  private void advance(final Duration duration) {
    now.addAndGet(duration.toMillis());
  }

  @Test
  void shouldLogOnlyOnceWithinTheInterval() {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofMinutes(1));

    // when
    for (int i = 0; i < 10; i++) {
      logger.debug("message");
    }

    // then
    verify(delegate, times(1)).debug("message");
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldReportSuppressedCallsAndStreakDurationOnNextLoggedMessage() {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofSeconds(10), clock);
    logger.debug("message");

    // when 9 calls are suppressed before the interval elapses
    for (int i = 0; i < 9; i++) {
      advance(Duration.ofSeconds(1));
      logger.debug("message");
    }
    advance(Duration.ofSeconds(1));
    logger.debug("message");

    // then
    verify(delegate).debug("message");
    verify(delegate).debug("message [9 suppressed since last log, ongoing for 10s]");
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldKeepReportingTheSameStreakWhileItContinues() {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofSeconds(10), clock);

    // when the calls continue for 70 seconds
    for (int i = 0; i <= 70; i++) {
      logger.debug("message");
      advance(Duration.ofSeconds(1));
    }

    // then the count restarts after each logged message, the duration keeps growing
    verify(delegate).debug("message [9 suppressed since last log, ongoing for 1m 0s]");
  }

  @Test
  void shouldNotChangeMessageWhenNothingWasSuppressed() {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofSeconds(10), clock);

    // when
    logger.debug("message");
    advance(Duration.ofSeconds(10));
    logger.debug("message");
    advance(Duration.ofSeconds(10));
    logger.debug("message");

    // then
    verify(delegate, times(3)).debug("message");
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldReportSuppressedCallsOfAnEndedStreakWithoutItsDuration() {
    // given a suppressed call
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofSeconds(10), clock);
    logger.debug("message");
    advance(Duration.ofSeconds(1));
    logger.debug("message");

    // when no call is made for longer than twice the interval
    advance(Duration.ofSeconds(25));
    logger.debug("message");

    // then the suppressed call is still reported, but the new streak has no duration yet
    verify(delegate).debug("message");
    verify(delegate).debug("message [1 suppressed since last log]");
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldKeepArgumentsWhenAppendingSuffix() {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofSeconds(10), clock);
    final var error = new RuntimeException("failure");
    logger.warn("first {}", "a", error);
    advance(Duration.ofSeconds(1));
    logger.warn("suppressed");

    // when
    advance(Duration.ofSeconds(9));
    logger.warn("second {}", "b", error);

    // then
    verify(delegate).warn("first {}", "a", error);
    verify(delegate).warn("second {} [1 suppressed since last log, ongoing for 10s]", "b", error);
  }

  @Test
  void shouldThrottleInterruptedThreadWithinTheInterval() {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofMinutes(1), clock);
    logger.debug("first");

    // when
    Thread.currentThread().interrupt();
    try {
      logger.debug("interrupted");

      // then the call is suppressed and the thread is still interrupted
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    verify(delegate).debug("first");
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldThrottleThreadThatStaysInterruptedAfterTheInterval() {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofSeconds(10), clock);
    logger.debug("first");
    advance(Duration.ofSeconds(1));
    logger.debug("suppressed");
    advance(Duration.ofSeconds(9));

    // when a thread keeps its interrupt flag set while logging, as a retry loop might
    Thread.currentThread().interrupt();
    try {
      for (int i = 0; i < 100; i++) {
        logger.debug("interrupted");
      }

      // then it is still interrupted
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }

    // and only its first call is logged, like for any other thread
    verify(delegate).debug("first");
    verify(delegate).debug("interrupted [1 suppressed since last log, ongoing for 10s]");
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldStopWaitingAndCountCallAsSuppressedWhenInterruptedWhileWaitingForLock()
      throws Exception {
    // given a lock held by the test, so that the first call has to wait for it
    final var lock = new ReentrantLock();
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofMinutes(1), clock, lock);
    final var interruptFlagAfterLogging = new AtomicBoolean();
    final var waiter =
        new Thread(
            () -> {
              logger.debug("waiter");
              interruptFlagAfterLogging.set(Thread.currentThread().isInterrupted());
            });
    // a daemon thread cannot keep the JVM alive if the test fails
    waiter.setDaemon(true);

    lock.lock();
    try {
      waiter.start();
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (!lock.hasQueuedThread(waiter) && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertThat(lock.hasQueuedThread(waiter)).isTrue();

      // when the waiting thread is interrupted
      waiter.interrupt();
      waiter.join(10_000);

      // then it stopped waiting for the lock and is still interrupted
      assertThat(waiter.isAlive()).isFalse();
      assertThat(interruptFlagAfterLogging).isTrue();
    } finally {
      lock.unlock();
      waiter.interrupt();
      waiter.join(10_000);
    }

    // and its call was counted as suppressed
    logger.debug("next");
    verify(delegate).debug("next [1 suppressed since last log]");
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldCountEverySuppressedCallWhenManyThreadsLog() throws InterruptedException {
    // given
    final var delegate = mock(Logger.class);
    final var logger = new ThrottledLogger(delegate, Duration.ofSeconds(10), clock);
    final int threads = 16;
    final int callsPerThread = 1_000;
    final var start = new CountDownLatch(1);
    final var executor = Executors.newFixedThreadPool(threads);

    try {
      // when the clock stands still, so that only the very first call is logged
      for (int i = 0; i < threads; i++) {
        executor.execute(
            () -> {
              try {
                start.await();
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              for (int c = 0; c < callsPerThread; c++) {
                logger.debug("message");
              }
            });
      }
      start.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
      advance(Duration.ofSeconds(10));
      logger.debug("message");

      // then no suppressed call was lost
      final int suppressed = threads * callsPerThread - 1;
      verify(delegate).debug("message");
      verify(delegate)
          .debug("message [" + suppressed + " suppressed since last log, ongoing for 10s]");
      verifyNoMoreInteractions(delegate);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldLogOnceWhenManyThreadsLogAtTheSameTime() throws InterruptedException {
    // given a delegate that blocks inside the first log call, so that every other thread has to
    // decide whether to log while the first one is still logging
    final var delegate = mock(Logger.class);
    final var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              release.await();
              return null;
            })
        .when(delegate)
        .debug("message");
    // the interval only has to outlast the test, so that it cannot expire while the threads race
    final var logger = new ThrottledLogger(delegate, Duration.ofMinutes(1));

    final int threads = 32;
    // one thread wins the interval and stays inside the delegate, all others must return
    final int callersThatMustNotLog = threads - 1;
    final var start = new CountDownLatch(1);
    final var returned = new CountDownLatch(callersThatMustNotLog);
    final var executor = Executors.newFixedThreadPool(threads);

    try {
      // when
      for (int i = 0; i < threads; i++) {
        executor.execute(
            () -> {
              try {
                start.await();
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              logger.debug("message");
              returned.countDown();
            });
      }
      start.countDown();

      // then all callers but the one that is logging return without logging
      assertThat(returned.await(10, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      verify(delegate, times(1)).debug("message");
      verifyNoMoreInteractions(delegate);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }
}
