/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import io.camunda.zeebe.engine.processing.streamprocessor.ProcessingResult;
import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamPlatform.ProcessingSchedulingService;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandsBuilder;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.util.function.Function;

public final class DueDateChecker implements StreamProcessorLifecycleAware {

  private long nextDueDate = -1L;
  private final long timerResolution;
  private final Function<CommandsBuilder, Long> nextDueDateSupplier;
  private ProcessingSchedulingService processingSchedulingService;
  private CommandsBuilder commandsBuilder;

  public DueDateChecker(
      final long timerResolution, final Function<CommandsBuilder, Long> nextDueDateFunction) {
    this.timerResolution = timerResolution;
    nextDueDateSupplier = nextDueDateFunction;
  }

  public void schedule(final long dueDate) {

    // We schedule only one runnable for all timers.
    // - The runnable is scheduled when the first timer is scheduled.
    // - If a new timer is scheduled which should be triggered before the current runnable is
    // executed then the runnable is canceled and re-scheduled with the new delay.
    // - Otherwise, we don't need to cancel the runnable. It will be rescheduled when it is
    // executed.

    final Duration delay = calculateDelayForNextRun(dueDate);

    processingSchedulingService.runWithDelay(delay, this::triggerEntities);
    nextDueDate = dueDate;

    // TODO WE COULD RETURN THE SCHEDULED TIMER, BUT TWO THINGS TO THIS
    //
    // a) is this really necessary? I see no real issue than it will just
    //    to nothing if there no more timers ?!
    // b) we have to introduce a separate interface to not pollute our impl with that details
    //
  }

  private ProcessingResult triggerEntities() {
    nextDueDate = nextDueDateSupplier.apply(commandsBuilder);

    // reschedule the runnable if there are timers left

    if (nextDueDate > 0) {
      final Duration delay = calculateDelayForNextRun(nextDueDate);
      processingSchedulingService.runWithDelay(delay, this::triggerEntities);
      return new ProcessingResult(commandsBuilder);
    } else {
      return ProcessingResult.empty();
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
  public void onRecovered(final ReadonlyProcessingContext context) {
    processingSchedulingService = context.getProcessingSchedulingService();
    commandsBuilder = context.getWriters().command();
    // check if timers are due after restart
    triggerEntities();
  }

  @Override
  public void onPaused() {
    // THIS IS NO LONGER NECESSARY
  }

  @Override
  public void onResumed() {
    // THIS IS NO LONGER NECESSARY
  }
}
