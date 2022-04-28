/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.processing.scheduled.DueDateChecker;
import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import java.util.function.Function;

public class DueDateTimerChecker implements StreamProcessorLifecycleAware {

  private static final long TIMER_RESOLUTION = Duration.ofMillis(100).toMillis();
  private final DueDateChecker dueDateChecker;

  public DueDateTimerChecker(final TimerInstanceState timerInstanceState) {
    dueDateChecker =
        new DueDateChecker(TIMER_RESOLUTION, new TriggerTimersSideEffect(timerInstanceState));
  }

  public void scheduleTimer(final long dueDate) {
    dueDateChecker.schedule(dueDate);
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    dueDateChecker.onRecovered(context);
  }

  @Override
  public void onClose() {
    dueDateChecker.onClose();
  }

  @Override
  public void onFailed() {
    dueDateChecker.onFailed();
  }

  @Override
  public void onPaused() {
    dueDateChecker.onPaused();
  }

  @Override
  public void onResumed() {
    dueDateChecker.onResumed();
  }

  protected static final class TriggerTimersSideEffect
      implements Function<TypedCommandWriter, Long> {

    private final TimerRecord timerRecord = new TimerRecord();

    private final TimerInstanceState timerInstanceState;

    public TriggerTimersSideEffect(final TimerInstanceState timerInstanceState) {
      this.timerInstanceState = timerInstanceState;
    }

    @Override
    public Long apply(final TypedCommandWriter typedCommandWriter) {
      return timerInstanceState.processTimersWithDueDateBefore(
          ActorClock.currentTimeMillis(),
          timer -> {
            timerRecord.reset();
            timerRecord
                .setElementInstanceKey(timer.getElementInstanceKey())
                .setProcessInstanceKey(timer.getProcessInstanceKey())
                .setDueDate(timer.getDueDate())
                .setTargetElementId(timer.getHandlerNodeId())
                .setRepetitions(timer.getRepetitions())
                .setProcessDefinitionKey(timer.getProcessDefinitionKey());

            typedCommandWriter.reset();
            typedCommandWriter.appendFollowUpCommand(
                timer.getKey(), TimerIntent.TRIGGER, timerRecord);

            return typedCommandWriter.flush() > 0; // means the write was successful
          });
    }
  }
}
