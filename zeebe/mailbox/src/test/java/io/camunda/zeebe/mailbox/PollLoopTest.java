/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

final class PollLoopTest {

  private final ManualScheduler scheduler = new ManualScheduler();

  @Test
  void shouldNotCallTheStepBeforeItIsStarted() {
    // given
    final var calls = new AtomicInteger();
    new PollLoop("test", scheduler, backoff(), () -> calls.incrementAndGet() > 0);

    // when
    scheduler.runAll();

    // then
    assertThat(calls).hasValue(0);
  }

  @Test
  void shouldWaitLongerWhileIdleAndShortWhenWorkWasFound() {
    // given
    final var results = new ArrayDeque<>(List.of(false, false, true, false));
    final var loop = new PollLoop("test", scheduler, backoff(), results::poll);

    // when
    loop.start();
    for (var i = 0; i < 4; i++) {
      scheduler.runNext();
    }

    // then
    assertThat(scheduler.delays)
        .extracting(Duration::toMillis)
        .containsExactly(10L, 20L, 40L, 10L, 20L);
  }

  @Test
  void shouldNotStartTwice() {
    // given
    final var loop = new PollLoop("test", scheduler, backoff(), () -> false);

    // when
    loop.start();
    loop.start();

    // then
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void shouldNotCallTheStepAfterItWasClosed() {
    // given
    final var calls = new AtomicInteger();
    final var loop = new PollLoop("test", scheduler, backoff(), () -> calls.incrementAndGet() > 0);
    loop.start();

    // when
    loop.close();
    scheduler.runAll();

    // then
    assertThat(calls).hasValue(0);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void shouldStopSchedulingWhenTheStepClosesTheLoop() {
    // given
    final var loopHolder = new PollLoop[1];
    loopHolder[0] =
        new PollLoop(
            "test",
            scheduler,
            backoff(),
            () -> {
              loopHolder[0].close();
              return true;
            });

    // when
    loopHolder[0].start();
    scheduler.runNext();

    // then
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void shouldIgnoreAStaleCallAfterRestart() {
    // given
    final var calls = new AtomicInteger();
    final var loop = new PollLoop("test", scheduler, backoff(), () -> calls.incrementAndGet() < 0);
    loop.start();
    loop.close();

    // when
    loop.start();
    scheduler.runAll(2);

    // then
    assertThat(calls).hasValue(1);
  }

  @Test
  void shouldKeepRunningWhenTheStepThrows() {
    // given
    final BooleanSupplier failing =
        () -> {
          throw new IllegalStateException("boom");
        };
    final var loop = new PollLoop("test", scheduler, backoff(), failing);

    // when
    loop.start();
    scheduler.runNext();

    // then
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void shouldStopWhenTheSchedulerIsClosed() {
    // given
    final Scheduler closed =
        (task, delay) -> {
          throw new RejectedExecutionException("closed");
        };
    final var loop = new PollLoop("test", closed, backoff(), () -> true);

    // when / then
    assertThatCode(
            () -> {
              loop.start();
              loop.start();
            })
        .doesNotThrowAnyException();
  }

  private static AdaptiveBackoff backoff() {
    return new AdaptiveBackoff(Duration.ofMillis(10), Duration.ofMillis(40));
  }

  /** Keeps the scheduled tasks until a test runs them. */
  private static final class ManualScheduler implements Scheduler {
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final List<Duration> delays = new ArrayList<>();

    @Override
    public void schedule(final Runnable task, final Duration delay) {
      tasks.add(task);
      delays.add(delay);
    }

    void runNext() {
      final var task = tasks.poll();
      if (task != null) {
        task.run();
      }
    }

    void runAll() {
      runAll(100);
    }

    void runAll(final int max) {
      for (var i = 0; i < max && !tasks.isEmpty(); i++) {
        runNext();
      }
    }

    int pending() {
      return tasks.size();
    }
  }
}
