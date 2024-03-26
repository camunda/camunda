/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.AtomicUtil;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class DueDateChecker implements StreamProcessorLifecycleAware {
  private final boolean scheduleAsync;
  private final long timerResolution;
  private final Function<TaskResultBuilder, Long> visitor;

  private ScheduleDelayed scheduleService;

  /**
   * Indicates whether the checker should reschedule itself. Controlled by the stream processor's
   * lifecycle events, e.g. {@link #onPaused()} and {@link #onResumed()}.
   */
  private boolean shouldRescheduleChecker;

  /**
   * Keeps track of the next execution of the checker. It is set to null when the checker has no
   * need to reschedule itself anymore.
   */
  private AtomicReference<NextExecution> nextExecution = new AtomicReference<>(null);

  public DueDateChecker(
      final long timerResolution,
      final boolean scheduleAsync,
      final Function<TaskResultBuilder, Long> visitor) {
    this.timerResolution = timerResolution;
    this.scheduleAsync = scheduleAsync;
    this.visitor = visitor;
  }

  TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    nextExecution = null;

    final long nextDueDate = visitor.apply(taskResultBuilder);

    // reschedule the runnable if there are timers left
    if (nextDueDate > 0) {
      schedule(nextDueDate);
    }

    return taskResultBuilder.build();
  }

  public void schedule(final long dueDate) {

    // We schedule only one runnable for all timers.
    // - The runnable is scheduled when the first timer is scheduled.
    // - If a new timer is scheduled which should be triggered before the current runnable is
    // executed then the runnable is canceled and re-scheduled with the new delay.
    // - Otherwise, we don't need to cancel the runnable. It will be rescheduled when it is
    // executed.

    if (!shouldRescheduleChecker) {
      return;
    }

    final var previouslyScheduled =
        AtomicUtil.update(
            nextExecution,
            currentlyScheduled -> {
              if (currentlyScheduled == null
                  || currentlyScheduled.nextDueDate() - dueDate > timerResolution) {
                final var delay = calculateDelayForNextRun(dueDate);
                final var task = scheduleService.runDelayed(delay, this::execute);
                return Optional.of(new NextExecution(dueDate, task));
              }
              return Optional.empty();
            },
            newlyScheduled -> newlyScheduled.task().cancel());

    if (previouslyScheduled != null) {
      // cancel the execution that we just replaced
      previouslyScheduled.task().cancel();
    }
  }

  /**
   * Calculates the delay for the next run so that it occurs at (or close to) due date. If due date
   * is in the future, the delay will be precise. If due date is in the past, now or in the very
   * near future, then a lower floor is applied to the delay. The lower floor is {@code
   * timerResolution}. This is to prevent the checker from being immediately rescheduled and thus
   * not giving any other tasks a chance to run.
   *
   * @param dueDate due date for which a scheduling delay is calculated
   * @return delay to hit the next due date; will be {@code >= timerResolution}
   */
  private Duration calculateDelayForNextRun(final long dueDate) {
    return Duration.ofMillis(Math.max(dueDate - ActorClock.currentTimeMillis(), timerResolution));
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    final var scheduleService = processingContext.getScheduleService();
    if (scheduleAsync) {
      this.scheduleService = scheduleService::runDelayedAsync;
    } else {
      this.scheduleService = scheduleService::runDelayed;
    }

    shouldRescheduleChecker = true;
    schedule(-1);
  }

  @Override
  public void onClose() {
    shouldRescheduleChecker = false;
  }

  @Override
  public void onFailed() {
    shouldRescheduleChecker = false;
  }

  @Override
  public void onPaused() {
    shouldRescheduleChecker = false;
  }

  @Override
  public void onResumed() {
    shouldRescheduleChecker = true;
    schedule(-1);
  }

  /**
   * Keeps track of the next execution of the checker.
   *
   * @param nextDueDate The due date of the next timer to be checked or -1 on the first execution,
   *     i.e. we don't know the next due date yet.
   * @param task The scheduled task for the next execution, can be used for canceling the task.
   */
  private record NextExecution(long nextDueDate, ScheduledTask task) {}

  /**
   * Abstracts over async and sync scheduling methods of {@link
   * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService}.
   */
  @FunctionalInterface
  interface ScheduleDelayed {
    /**
     * Implemented by either {@link
     * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService#runDelayed(Duration, Task)}
     * or {@link
     * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService#runDelayedAsync(Duration,
     * Task)}
     */
    ScheduledTask runDelayed(final Duration delay, final Task task);
  }
}
