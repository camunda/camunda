/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.timer;

import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;

public class DueDateTimerChecker implements StreamProcessorLifecycleAware {

  private static final long TIMER_RESOLUTION = Duration.ofMillis(100).toMillis();

  private final TimerRecord timerRecord = new TimerRecord();

  private final WorkflowState workflowState;
  private ActorControl actor;
  private TypedStreamWriter streamWriter;

  private ScheduledTimer scheduledTimer;
  private long nextDueDate = -1L;

  public DueDateTimerChecker(final WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  public void scheduleTimer(final TimerInstance timer) {

    // We schedule only one runnable for all timers.
    // - The runnable is scheduled when the first timer is scheduled.
    // - If a new timer is scheduled which should be triggered before the current runnable is
    // executed then the runnable is canceled and re-scheduled with the new duration.
    // - Otherwise, we don't need to cancel the runnable. It will be rescheduled when it is
    // executed.

    final Duration duration =
        Duration.ofMillis(timer.getDueDate() - ActorClock.currentTimeMillis());

    if (scheduledTimer == null) {
      scheduledTimer = actor.runDelayed(duration, this::triggerTimers);
      nextDueDate = timer.getDueDate();

    } else if (nextDueDate - timer.getDueDate() > TIMER_RESOLUTION) {
      scheduledTimer.cancel();

      scheduledTimer = actor.runDelayed(duration, this::triggerTimers);
      nextDueDate = timer.getDueDate();
    }
  }

  private void triggerTimers() {
    nextDueDate =
        workflowState
            .getTimerState()
            .findTimersWithDueDateBefore(ActorClock.currentTimeMillis(), this::triggerTimer);

    // reschedule the runnable if there are timers left

    if (nextDueDate > 0) {
      final Duration duration = Duration.ofMillis(nextDueDate - ActorClock.currentTimeMillis());
      scheduledTimer = actor.runDelayed(duration, this::triggerTimers);

    } else {
      scheduledTimer = null;
    }
  }

  private boolean triggerTimer(TimerInstance timer) {
    timerRecord.reset();
    timerRecord
        .setElementInstanceKey(timer.getElementInstanceKey())
        .setWorkflowInstanceKey(timer.getWorkflowInstanceKey())
        .setDueDate(timer.getDueDate())
        .setTargetElementId(timer.getHandlerNodeId())
        .setRepetitions(timer.getRepetitions())
        .setWorkflowKey(timer.getWorkflowKey());

    streamWriter.reset();
    streamWriter.appendFollowUpCommand(timer.getKey(), TimerIntent.TRIGGER, timerRecord);

    return streamWriter.flush() > 0;
  }

  @Override
  public void onOpen(final ReadonlyProcessingContext processingContext) {
    this.actor = processingContext.getActor();
    streamWriter = processingContext.getLogStreamWriter();
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext processingContext) {
    // check if timers are due after restart
    triggerTimers();
  }
}
