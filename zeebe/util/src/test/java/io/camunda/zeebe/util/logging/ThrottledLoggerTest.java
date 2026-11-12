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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

final class ThrottledLoggerTest {

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
