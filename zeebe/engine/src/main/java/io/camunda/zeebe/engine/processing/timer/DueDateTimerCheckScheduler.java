/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.processing.scheduled.api.Outcome;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;

/**
 * Triggers BPMN timers (start events, boundary events, intermediate catch events) once their due
 * date is reached.
 *
 * <p>On-demand: returns {@link Outcome.AwaitDueAt} for the next due-date so the runtime sleeps
 * until then. External callers ({@code TimerStartEventSubscriptionManager} and friends) call {@code
 * managed.requestRun(dueDate)} via {@link #scheduleTimer} whenever a new timer is created with an
 * earlier due-date than the currently-scheduled run.
 *
 * <p>Cooperative yielding: when many timers are due at once, the task polls {@link
 * TaskContext#shouldYield()} between entries and returns {@link Outcome.YieldNow} so the actor
 * thread can serve other work. The schedule's yield budget controls how long a single run may
 * consume.
 */
public final class DueDateTimerCheckScheduler implements ScheduledTask {

  private final TimerInstanceState timerInstanceState;

  // Reused across iterations within a single run to avoid garbage.
  private final TimerRecord timerRecord = new TimerRecord();

  public DueDateTimerCheckScheduler(final TimerInstanceState timerInstanceState) {
    this.timerInstanceState = timerInstanceState;
  }

  @Override
  public String name() {
    return "timer-due-date-check";
  }

  @Override
  public Outcome run(final TaskContext ctx) {
    final long now = ctx.clock().millis();

    final long nextDueDate =
        timerInstanceState.processTimersWithDueDateBefore(
            now,
            timer -> {
              if (ctx.shouldYield()) {
                return false;
              }
              timerRecord.reset();
              timerRecord
                  .setElementInstanceKey(timer.getElementInstanceKey())
                  .setProcessInstanceKey(timer.getProcessInstanceKey())
                  .setDueDate(timer.getDueDate())
                  .setTargetElementId(timer.getHandlerNodeId())
                  .setRepetitions(timer.getRepetitions())
                  .setProcessDefinitionKey(timer.getProcessDefinitionKey())
                  .setTenantId(timer.getTenantId());
              return ctx.sink().append(timer.getKey(), TimerIntent.TRIGGER, timerRecord);
            });

    if (nextDueDate > 0) {
      return new Outcome.AwaitDueAt(nextDueDate);
    }
    // No more timers; if the visitor stopped because we yielded or the batch was full, the
    // runtime will reschedule on the YieldNow path. Plain Idle here means "actually nothing to do".
    return ctx.shouldYield() ? Outcome.YIELD_NOW : Outcome.IDLE;
  }
}
