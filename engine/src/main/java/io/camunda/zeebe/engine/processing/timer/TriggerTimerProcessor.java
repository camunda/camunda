/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.model.bpmn.util.time.Interval;
import io.camunda.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Instant;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class TriggerTimerProcessor implements TypedRecordProcessor<TimerRecord> {

  private static final String NO_TIMER_FOUND_MESSAGE =
      "Expected to trigger timer with key '%d', but no such timer was found";
  private static final String NO_ACTIVE_TIMER_MESSAGE =
      "Expected to trigger a timer with key '%d', but the timer is not active anymore";
  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final CatchEventBehavior catchEventBehavior;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final MutableTimerInstanceState timerInstanceState;
  private final ExpressionProcessor expressionProcessor;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  private final EventHandle eventHandle;

  public TriggerTimerProcessor(
      final MutableZeebeState zeebeState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers) {
    catchEventBehavior = bpmnBehaviors.catchEventBehavior();
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();

    processState = zeebeState.getProcessState();
    elementInstanceState = zeebeState.getElementInstanceState();
    timerInstanceState = zeebeState.getTimerState();
    keyGenerator = zeebeState.getKeyGenerator();
    eventHandle =
        new EventHandle(
            keyGenerator,
            zeebeState.getEventScopeInstanceState(),
            writers,
            processState,
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());
  }

  @Override
  public void processRecord(final TypedRecord<TimerRecord> record) {
    final var timer = record.getValue();
    final var elementInstanceKey = timer.getElementInstanceKey();
    final var processDefinitionKey = timer.getProcessDefinitionKey();
    final var timerInstance = timerInstanceState.get(elementInstanceKey, record.getKey());
    if (timerInstance == null) {
      rejectionWriter.appendRejection(
          record, RejectionType.NOT_FOUND, String.format(NO_TIMER_FOUND_MESSAGE, record.getKey()));
      return;
    }

    final var catchEvent =
        processState.getFlowElement(
            processDefinitionKey, timer.getTargetElementIdBuffer(), ExecutableCatchEvent.class);
    if (isStartEvent(elementInstanceKey)) {
      final long processInstanceKey = keyGenerator.nextKey();
      timer.setProcessInstanceKey(processInstanceKey);
      stateWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);
      eventHandle.activateProcessInstanceForStartEvent(
          processDefinitionKey, processInstanceKey, timer.getTargetElementIdBuffer(), NO_VARIABLES);
    } else {
      final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
      if (!eventHandle.canTriggerElement(elementInstance, timer.getTargetElementIdBuffer())) {
        rejectNoActiveTimer(record);
        return;
      }

      stateWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);
      eventHandle.activateElement(catchEvent, elementInstanceKey, elementInstance.getValue());
    }

    if (shouldReschedule(timer)) {
      rescheduleTimer(timer, catchEvent);
    }
  }

  private void rejectNoActiveTimer(final TypedRecord<TimerRecord> record) {
    rejectionWriter.appendRejection(
        record,
        RejectionType.INVALID_STATE,
        String.format(NO_ACTIVE_TIMER_MESSAGE, record.getKey()));
  }

  private boolean isStartEvent(final long elementInstanceKey) {
    return elementInstanceKey < 0;
  }

  private boolean shouldReschedule(final TimerRecord timer) {
    return timer.getRepetitions() == RepeatingInterval.INFINITE || timer.getRepetitions() > 1;
  }

  private void rescheduleTimer(final TimerRecord record, final ExecutableCatchEvent event) {
    final Either<Failure, Timer> timer =
        event.getTimerFactory().apply(expressionProcessor, record.getElementInstanceKey());
    if (timer.isLeft()) {
      final String message =
          String.format(
              "Expected to reschedule repeating timer for element with id '%s', but an error occurred: %s",
              BufferUtil.bufferAsString(event.getId()), timer.getLeft().getMessage());
      throw new IllegalStateException(message);
      // todo(#4208): raise incident instead of throwing an exception
    }

    final Timer refreshedTimer = refreshTimer(timer.get(), record);
    catchEventBehavior.subscribeToTimerEvent(
        record.getElementInstanceKey(),
        record.getProcessInstanceKey(),
        record.getProcessDefinitionKey(),
        event.getId(),
        refreshedTimer);
  }

  private Timer refreshTimer(final Timer timer, final TimerRecord record) {
    if (timer instanceof CronTimer) {
      return timer;
    }

    int repetitions = record.getRepetitions();
    if (repetitions != RepeatingInterval.INFINITE) {
      repetitions--;
    }

    // Use the timer's last due date instead of the current time to avoid a time shift.
    final Interval refreshedInterval =
        timer.getInterval().withStart(Instant.ofEpochMilli(record.getDueDate()));
    return new RepeatingInterval(repetitions, refreshedInterval);
  }
}
