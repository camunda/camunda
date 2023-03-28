/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import java.util.function.Function;

public final class DueDateChecker implements StreamProcessorLifecycleAware {

  private ProcessingScheduleService scheduleService;

  private boolean checkerRunning;
  private boolean shouldRescheduleChecker;

  private long nextDueDate = -1L;
  private final long timerResolution;
  private final Function<TaskResultBuilder, Long> nextDueDateSupplier;
  private final TriggerEntitiesTask triggerEntitiesTask;
  private final InstantSource clock;

  public DueDateChecker(
      final long timerResolution,
      final Function<TaskResultBuilder, Long> nextDueDateFunction,
      final InstantSource clock) {
    this.timerResolution = timerResolution;
    nextDueDateSupplier = nextDueDateFunction;
    this.clock = clock;
    triggerEntitiesTask = new TriggerEntitiesTask();
  }

  public void schedule(final long dueDate) {

    // We schedule only one runnable for all timers.
    // - The runnable is scheduled when the first timer is scheduled.
    // - If a new timer is scheduled which should be triggered before the current runnable is
    // executed then the runnable is canceled and re-scheduled with the new delay.
    // - Otherwise, we don't need to cancel the runnable. It will be rescheduled when it is
    // executed.

    final Duration delay = calculateDelayForNextRun(dueDate);

    if (shouldRescheduleChecker) {
      if (!checkerRunning) {
        scheduleService.runDelayed(delay, triggerEntitiesTask);
        nextDueDate = dueDate;
        checkerRunning = true;
      } else if (nextDueDate - dueDate > timerResolution) {
        scheduleService.runDelayed(delay, triggerEntitiesTask);
        nextDueDate = dueDate;
      }
    }
  }

  private void scheduleTriggerEntitiesTask() {
    if (shouldRescheduleChecker) {
      scheduleService.runDelayed(Duration.ZERO, triggerEntitiesTask);
    } else {
      checkerRunning = false;
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
    return Duration.ofMillis(Math.max(dueDate - clock.millis(), timerResolution));
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    scheduleService = processingContext.getScheduleService();
    shouldRescheduleChecker = true;
    // check if timers are due after restart
    scheduleTriggerEntitiesTask();
  }

  @Override
  public void onPaused() {
    shouldRescheduleChecker = false;
    nextDueDate = -1;
  }

  @Override
  public void onResumed() {
    shouldRescheduleChecker = true;
    if (!checkerRunning) {
      scheduleTriggerEntitiesTask();
    }
  }

  private final class TriggerEntitiesTask implements Task {

    @Override
    public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
      if (shouldRescheduleChecker) {
        nextDueDate = nextDueDateSupplier.apply(taskResultBuilder);

        // reschedule the runnable if there are timers left

        if (nextDueDate > 0) {
          final Duration delay = calculateDelayForNextRun(nextDueDate);
          scheduleService.runDelayed(delay, this);
          checkerRunning = true;
        } else {
          checkerRunning = false;
        }
      } else {
        checkerRunning = false;
      }
      return taskResultBuilder.build();
    }
  }
}
