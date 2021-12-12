/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ScheduledTimer;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.util.function.Function;

public final class DueDateChecker implements StreamProcessorLifecycleAware {

  private ActorControl actor;
  private TypedStreamWriter streamWriter;

  private ScheduledTimer scheduledTimer;
  private long nextDueDate = -1L;
  private final long timerResolution;
  private final Function<TypedCommandWriter, Long> nextDueDateSupplier;

  public DueDateChecker(
      final long timerResolution, final Function<TypedCommandWriter, Long> nextDueDateFunction) {
    this.timerResolution = timerResolution;
    nextDueDateSupplier = nextDueDateFunction;
  }

  public void schedule(final long dueDate) {

    // We schedule only one runnable for all timers.
    // - The runnable is scheduled when the first timer is scheduled.
    // - If a new timer is scheduled which should be triggered before the current runnable is
    // executed then the runnable is canceled and re-scheduled with the new duration.
    // - Otherwise, we don't need to cancel the runnable. It will be rescheduled when it is
    // executed.

    final Duration duration = Duration.ofMillis(dueDate - ActorClock.currentTimeMillis());

    if (scheduledTimer == null) {
      scheduledTimer = actor.runDelayed(duration, this::triggerEntities);
      nextDueDate = dueDate;

    } else if (nextDueDate - dueDate > timerResolution) {
      scheduledTimer.cancel();

      scheduledTimer = actor.runDelayed(duration, this::triggerEntities);
      nextDueDate = dueDate;
    }
  }

  private void triggerEntities() {
    nextDueDate = nextDueDateSupplier.apply(streamWriter);

    // reschedule the runnable if there are timers left

    if (nextDueDate > 0) {
      final Duration duration = Duration.ofMillis(nextDueDate - ActorClock.currentTimeMillis());
      scheduledTimer = actor.runDelayed(duration, this::triggerEntities);

    } else {
      scheduledTimer = null;
    }
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext processingContext) {
    actor = processingContext.getActor();
    streamWriter = processingContext.getLogStreamWriter();
    // check if timers are due after restart
    triggerEntities();
  }

  @Override
  public void onPaused() {
    if (scheduledTimer != null) {
      scheduledTimer.cancel();
      scheduledTimer = null;
    }
  }

  @Override
  public void onResumed() {
    if (scheduledTimer == null) {
      triggerEntities();
    }
  }
}
