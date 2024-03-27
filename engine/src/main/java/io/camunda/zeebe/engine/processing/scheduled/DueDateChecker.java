/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.scheduler.clock.ActorClock;
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
   * Keeps track of the next execution of the checker. Value can be null if there is no scheduled
   * execution known.
   */
  private final AtomicReference<NextExecution> nextExecution = new AtomicReference<>(null);

  public DueDateChecker(
      final long timerResolution,
      final boolean scheduleAsync,
      final Function<TaskResultBuilder, Long> visitor) {
    this.timerResolution = timerResolution;
    this.scheduleAsync = scheduleAsync;
    this.visitor = visitor;
  }

  TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    // There is a benign edge case where we are not supposed to set nextExecution to null here.
    // If this execution was supposed to be cancelled because an earlier execution was scheduled
    // instead, nextExecution would hold that earlier execution. We still overwrite it with null and
    // thus forget that we planned an execution. The next time something is scheduled, we will
    // observe the null value and thus decide to schedule something new without cancelling what's
    // already scheduled. While we try to avoid this, we can't prevent it entirely anyway because
    // cancellation of scheduled executions is best-effort and does not reliably prevent execution.
    nextExecution.set(null);

    final long nextDueDate = visitor.apply(taskResultBuilder);

    // reschedule the runnable if there are timers left
    if (nextDueDate > 0) {
      schedule(nextDueDate);
    }

    return taskResultBuilder.build();
  }

  public void schedule(final long dueDate) {
    if (!shouldRescheduleChecker) {
      return;
    }
    final var replacedExecution =
        AtomicUtil.replace(
            nextExecution,
            currentlyScheduled -> {
              final var now = ActorClock.currentTimeMillis();
              final var scheduleFor = now + Math.max(dueDate - now, timerResolution);
              if (currentlyScheduled == null
                  || currentlyScheduled.scheduledFor() - scheduleFor > timerResolution) {
                final var delay = Duration.ofMillis(scheduleFor - now);
                final var task = scheduleService.runDelayed(delay, this::execute);
                return Optional.of(new NextExecution(scheduleFor, task));
              }
              return Optional.empty();
            },
            newlyScheduled -> newlyScheduled.task().cancel());

    if (replacedExecution != null) {
      replacedExecution.task().cancel();
    }
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
   * @param scheduledFor The deadline in ms for when this execution is scheduled.
   * @param task The scheduled task for the next execution, can be used for canceling the task.
   */
  private record NextExecution(long scheduledFor, ScheduledTask task) {}

  /** Abstracts over async and sync scheduling methods of {@link ProcessingScheduleService}. */
  @FunctionalInterface
  interface ScheduleDelayed {
    /**
     * Implemented by either {@link ProcessingScheduleService#runDelayed(Duration, Task)} or {@link
     * ProcessingScheduleService#runDelayedAsync(Duration, Task)}
     */
    ScheduledTask runDelayed(final Duration delay, final Task task);
  }
}
