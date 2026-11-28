/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SelfSchedulingTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(SelfSchedulingTaskTest.class);
  private static final Duration TASK_DELAY = Duration.ofMillis(1234);
  private static final Duration ERROR_DELAY = Duration.ofMillis(99);

  private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
  private final AtomicInteger taskCalls = new AtomicInteger();

  @Test
  void shouldNotRunTaskWhilePaused() {
    // given
    final var task = newTask(() -> true);

    // when
    task.run();

    // then
    assertThat(taskCalls.get()).isZero();
    verify(executor).schedule(task, ReschedulingTask.PAUSE_CHECK_DELAY_MS, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRescheduleWithErrorDelayWhenPauseCheckFails() {
    // given
    final var task =
        newTask(
            () -> {
              throw new RuntimeException("pause check failed");
            });

    // when
    task.run();

    // then
    assertThat(taskCalls.get()).isZero();
    verify(executor).schedule(task, ERROR_DELAY.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRunTaskAfterResume() {
    // given
    final var paused = new AtomicBoolean(true);
    final var task = newTask(paused::get);

    // when
    task.run();
    paused.set(false);
    task.run();

    // then
    assertThat(taskCalls.get()).isEqualTo(1);
    verify(executor).schedule(task, ReschedulingTask.PAUSE_CHECK_DELAY_MS, TimeUnit.MILLISECONDS);
    verify(executor).schedule(task, TASK_DELAY.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRunTaskWhenNotPaused() {
    // given
    final var task =
        new SelfSchedulingTask(
            "test",
            () -> {
              taskCalls.incrementAndGet();
              return TASK_DELAY;
            },
            () -> ERROR_DELAY,
            executor,
            LOGGER);

    // when
    task.run();

    // then
    assertThat(taskCalls.get()).isEqualTo(1);
    verify(executor).schedule(task, TASK_DELAY.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldLogPauseOnceAtInfoWhilePaused() {
    // given
    final var mockLogger = mock(Logger.class);
    final var task = newTask(() -> true, mockLogger);

    // when
    task.run();
    task.run();
    task.run();

    // then
    verify(mockLogger, times(1))
        .info(
            "Background task '{}' is paused because exporting is paused, checking again every {}ms",
            "test",
            ReschedulingTask.PAUSE_CHECK_DELAY_MS);
  }

  @Test
  void shouldLogResumeAtInfoAfterPause() {
    // given
    final var mockLogger = mock(Logger.class);
    final var paused = new AtomicBoolean(true);
    final var task = newTask(paused::get, mockLogger);

    // when
    task.run();
    paused.set(false);
    task.run();
    task.run();

    // then
    verify(mockLogger, times(1))
        .info("Background task '{}' is no longer paused, running again", "test");
  }

  private SelfSchedulingTask newTask(final PauseStateSupplier pauseState) {
    return newTask(pauseState, LOGGER);
  }

  private SelfSchedulingTask newTask(final PauseStateSupplier pauseState, final Logger logger) {
    return new SelfSchedulingTask(
        "test",
        () -> {
          taskCalls.incrementAndGet();
          return TASK_DELAY;
        },
        () -> ERROR_DELAY,
        executor,
        logger,
        pauseState);
  }
}
