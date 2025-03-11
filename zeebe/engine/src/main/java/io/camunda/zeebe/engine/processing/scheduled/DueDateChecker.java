/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import io.camunda.zeebe.engine.processing.scheduled.DueDateChecker.NextExecution.None;
import io.camunda.zeebe.engine.processing.scheduled.DueDateChecker.NextExecution.Scheduled;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.AtomicUtil;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The Due Date Checker is a special purpose checker (for due date related tasks) that doesn't
 * execute periodically but can be scheduled to run at a specific due date, i.e. it can idle for
 * extended periods of time and only runs when needed.
 *
 * <p>This class is thread safe and can be used concurrently. However, it cannot entirely prevent
 * that multiple executions are scheduled. See the comment in {@link #execute(TaskResultBuilder)}
 * for details.
 */
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
  private final AtomicReference<NextExecution> nextExecution = new AtomicReference<>(new None());

  private final InstantSource clock;

  /**
   * @param timerResolution The resolution in ms for the timer
   * @param scheduleAsync Whether to schedule the execution happens asynchronously or not
   * @param visitor Function that runs the task and returns the next due date or -1 if there is none
   */
  public DueDateChecker(
      final long timerResolution,
      final boolean scheduleAsync,
      final Function<TaskResultBuilder, Long> visitor,
      final InstantSource clock) {
    this.timerResolution = timerResolution;
    this.scheduleAsync = scheduleAsync;
    this.visitor = visitor;
    this.clock = clock;
  }

  TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    // There is a benign edge case where we are not supposed to set nextExecution to None here.
    // If this execution was supposed to be cancelled because an earlier execution was scheduled
    // instead, nextExecution would hold that earlier execution. We still overwrite it with None and
    // thus forget that we planned an execution. The next time something is scheduled, we will
    // observe the None value and thus decide to schedule something new without cancelling what's
    // already scheduled. While we try to avoid this, we can't prevent it entirely anyway because
    // cancellation of scheduled executions is best-effort and does not reliably prevent execution.
    nextExecution.set(new None());

    final long nextDueDate = visitor.apply(taskResultBuilder);

    // reschedule the runnable if there are timers left
    if (nextDueDate > 0) {
      schedule(nextDueDate);
    }

    return taskResultBuilder.build();
  }

  /**
   * Schedules the next execution of the checker and stores it in {@link #nextExecution}.
   *
   * <p>When called it guarantees that there is an execution scheduled at or before the provided due
   * date within the {@link #timerResolution}.
   *
   * <p>If there is no execution scheduled, it always schedules a new one. If there is already an
   * execution scheduled for a later time (within the timer resolution), that execution is cancelled
   * and replaced by the new one. In all other cases, no new execution is scheduled.
   *
   * <p>It is guaranteed that the next execution is scheduled at least {@link #timerResolution} ms
   * into the future. For example when the due date is in the past, now or in the very near future.
   * This is to prevent the checker from being immediately rescheduled and thus not giving any other
   * tasks a chance to run.
   *
   * <p>This method is thread safe and can be called concurrently. On concurrent scheduling, this
   * execution is cancelled and rescheduled.
   *
   * @param dueDate The due date for the next execution
   */
  public void schedule(final long dueDate) {
    if (!shouldRescheduleChecker) {
      return;
    }
    final var replacedExecution =
        AtomicUtil.replace(
            nextExecution,
            currentlyPlanned -> {
              final var now = clock.millis();
              final long scheduleFor = now + Math.max(dueDate - now, timerResolution);
              if (!(currentlyPlanned instanceof final Scheduled currentlyScheduled)
                  || (currentlyScheduled.scheduledFor() - scheduleFor > timerResolution)) {
                final var task = scheduleService.runAt(scheduleFor, this::execute);
                return Optional.of(new Scheduled(scheduleFor, task));
              }
              return Optional.empty();
            },
            NextExecution::cancel);

    if (replacedExecution != null) {
      replacedExecution.cancel();
    }
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    final var scheduleService = processingContext.getScheduleService();
    if (scheduleAsync) {
      this.scheduleService = scheduleService::runAtAsync;
    } else {
      this.scheduleService = scheduleService::runAt;
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
   * Abstracts over async and sync scheduling methods of {@link
   * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService}.
   */
  @FunctionalInterface
  interface ScheduleDelayed {
    /**
     * Implemented by either {@link
     * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService#runAt(long, Task)} or {@link
     * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService#runAtAsync(long, Task)}
     */
    ScheduledTask runAt(long timestamp, final Task task);
  }

  interface NextExecution {

    void cancel();

    /** Sentinel value to signal that nothing is scheduled. */
    record None() implements NextExecution {

      @Override
      public void cancel() {}
    }

    /**
     * Keeps track of the next execution of the checker.
     *
     * @param scheduledFor The deadline in ms for when this execution is scheduled.
     * @param task The scheduled task for the next execution, can be used for canceling the task.
     */
    record Scheduled(long scheduledFor, ScheduledTask task) implements NextExecution {

      @Override
      public void cancel() {
        task.cancel();
      }
    }
  }
}
