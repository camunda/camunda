/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.RegeneratingTask;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class DueDateCheckSchedulerTest {

  private static final long NOW = 1_000_000L;

  @Test
  public void shouldNotScheduleTwoTasks() {
    // given
    final var timerResolution = 100;
    final var dueDateChecker =
        new DueDateCheckScheduler(timerResolution, false, (builder) -> 0L, InstantSource.system());
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    final var currentTimeMillis = System.currentTimeMillis();
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second

    // then
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
  }

  @Test
  public void shouldScheduleForAnEarlierTasks() {
    // given
    final var timerResolution = 100;
    final var dueDateChecker =
        new DueDateCheckScheduler(timerResolution, false, (builder) -> 0L, InstantSource.system());
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runAt(anyLong(), any(Task.class))).thenReturn(mockScheduledTask);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    final var currentTimeMillis = System.currentTimeMillis();
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second
    dueDateChecker.schedule(currentTimeMillis + 100); // in 100 millis

    // then
    verify(mockScheduleService, times(2)).runAt(anyLong(), any(Task.class));
    verify(mockScheduledTask).cancel();
  }

  @Test
  public void shouldRescheduleAutomatically() {
    // given
    final Function<TaskResultBuilder, Long> visitor =
        (builder) -> InstantSource.system().millis() + 1000L;

    final var timerResolution = 100;
    final var dueDateChecker =
        new DueDateCheckScheduler(timerResolution, false, visitor, InstantSource.system());
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runAt(anyLong(), any(Task.class))).thenReturn(mockScheduledTask);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    dueDateChecker.execute(mock(TaskResultBuilder.class));

    // then
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
  }

  @Test
  public void shouldScheduleEarlierIfRescheduledAutomatically() {
    // given
    final Function<TaskResultBuilder, Long> visitor =
        (builder) -> InstantSource.system().millis() + 1000L;

    final var timerResolution = 100;
    final var dueDateChecker =
        new DueDateCheckScheduler(timerResolution, false, visitor, InstantSource.system());
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runAt(anyLong(), any(Task.class))).thenReturn(mockScheduledTask);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    dueDateChecker.execute(mock(TaskResultBuilder.class));
    // expect that there is a next execution scheduled after execution
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    dueDateChecker.schedule(InstantSource.system().millis() + 100); // in one second

    // then
    verify(mockScheduleService).runAt(anyLong(), any(Task.class));
    verify(mockScheduledTask).cancel();
  }

  @Test
  void shouldKeepExecutionWithinBackoffWhenResultIsAbandoned() {
    // given more timers are due right away, so the checker schedules its next execution soon
    final var clock = InstantSource.fixed(Instant.ofEpochMilli(NOW));
    final var dueDateChecker = new DueDateCheckScheduler(100, false, (builder) -> NOW, clock);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runAt(anyLong(), any(Task.class))).thenReturn(mockScheduledTask);
    final var checkerTask = recover(dueDateChecker, mockScheduleService);
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    verify(mockScheduleService).runAt(eq(NOW + 100), any(Task.class));
    Mockito.clearInvocations(mockScheduleService, mockScheduledTask);

    // when
    checkerTask.onResultAbandoned();

    // then
    verify(mockScheduleService, never()).runAt(anyLong(), any(Task.class));
    verify(mockScheduledTask, never()).cancel();
  }

  @Test
  void shouldScheduleEarlierExecutionWhenResultIsAbandoned() {
    // given the next timer is due much later
    final var clock = InstantSource.fixed(Instant.ofEpochMilli(NOW));
    final var dueDateChecker =
        new DueDateCheckScheduler(100, false, (builder) -> NOW + 3_600_000, clock);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runAt(anyLong(), any(Task.class))).thenReturn(mockScheduledTask);
    final var checkerTask = recover(dueDateChecker, mockScheduleService);
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    verify(mockScheduleService).runAt(eq(NOW + 3_600_000), any(Task.class));
    Mockito.clearInvocations(mockScheduleService, mockScheduledTask);

    // when
    checkerTask.onResultAbandoned();

    // then
    verify(mockScheduleService).runAt(eq(NOW + 100), any(Task.class));
    verify(mockScheduledTask).cancel();
  }

  @Test
  void shouldScheduleExecutionWhenResultIsAbandonedAndNoTimersAreLeft() {
    // given no timers are left, so the checker schedules no next execution
    final var clock = InstantSource.fixed(Instant.ofEpochMilli(NOW));
    final var dueDateChecker = new DueDateCheckScheduler(100, false, (builder) -> -1L, clock);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runAt(anyLong(), any(Task.class))).thenReturn(mockScheduledTask);
    final var checkerTask = recover(dueDateChecker, mockScheduleService);
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    Mockito.clearInvocations(mockScheduleService, mockScheduledTask);

    // when
    checkerTask.onResultAbandoned();

    // then
    verify(mockScheduleService).runAt(eq(NOW + 100), any(Task.class));
    verify(mockScheduledTask, never()).cancel();
  }

  @Test
  void shouldNotScheduleTwoExecutionsAfterResultIsAbandoned() {
    // given
    final var clock = InstantSource.fixed(Instant.ofEpochMilli(NOW));
    final var dueDateChecker = new DueDateCheckScheduler(100, false, (builder) -> -1L, clock);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var mockScheduledTask = mock(ScheduledTask.class);
    when(mockScheduleService.runAt(anyLong(), any(Task.class))).thenReturn(mockScheduledTask);
    final var checkerTask = recover(dueDateChecker, mockScheduleService);
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    checkerTask.onResultAbandoned();
    Mockito.clearInvocations(mockScheduleService, mockScheduledTask);

    // when a timer is scheduled that is due after the backoff
    dueDateChecker.schedule(NOW + 5000);

    // then
    verify(mockScheduleService, never()).runAt(anyLong(), any(Task.class));
    verify(mockScheduledTask, never()).cancel();
  }

  @Test
  void shouldNotScheduleExecutionWhenResultIsAbandonedWhilePaused() {
    // given
    final var clock = InstantSource.fixed(Instant.ofEpochMilli(NOW));
    final var dueDateChecker = new DueDateCheckScheduler(100, false, (builder) -> -1L, clock);
    final var mockScheduleService = mock(ProcessingScheduleService.class);
    final var checkerTask = recover(dueDateChecker, mockScheduleService);
    dueDateChecker.execute(mock(TaskResultBuilder.class));
    dueDateChecker.onPaused();
    Mockito.clearInvocations(mockScheduleService);

    // when
    checkerTask.onResultAbandoned();

    // then
    verify(mockScheduleService, never()).runAt(anyLong(), any(Task.class));
  }

  /** Recovers the checker and returns the task that it scheduled to check the due dates. */
  private static RegeneratingTask recover(
      final DueDateCheckScheduler dueDateChecker,
      final ProcessingScheduleService mockScheduleService) {
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);

    final var scheduledTask = ArgumentCaptor.forClass(Task.class);
    verify(mockScheduleService).runAt(anyLong(), scheduledTask.capture());
    Mockito.clearInvocations(mockScheduleService);
    return assertThat(scheduledTask.getValue())
        .describedAs("the checker can regenerate its result after it was abandoned")
        .asInstanceOf(type(RegeneratingTask.class))
        .actual();
  }
}
