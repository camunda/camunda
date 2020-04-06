/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.timer;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.EventHandle;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.model.bpmn.util.time.Interval;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class TriggerTimerProcessor implements TypedRecordProcessor<TimerRecord> {
  private static final String NO_TIMER_FOUND_MESSAGE =
      "Expected to trigger timer with key '%d', but no such timer was found";
  private static final String NO_ACTIVE_TIMER_MESSAGE =
      "Expected to trigger a timer with key '%d', but the timer is not active anymore";

  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final CatchEventBehavior catchEventBehavior;
  private final WorkflowState workflowState;
  private final EventHandle eventHandle;
  private final ExpressionProcessor expressionProcessor;

  public TriggerTimerProcessor(
      final ZeebeState zeebeState,
      final CatchEventBehavior catchEventBehavior,
      final ExpressionProcessor expressionProcessor) {
    this.catchEventBehavior = catchEventBehavior;
    this.expressionProcessor = expressionProcessor;
    workflowState = zeebeState.getWorkflowState();

    eventHandle =
        new EventHandle(zeebeState.getKeyGenerator(), workflowState.getEventScopeInstanceState());
  }

  @Override
  public void processRecord(
      final TypedRecord<TimerRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final TimerRecord timer = record.getValue();
    final long elementInstanceKey = timer.getElementInstanceKey();

    final TimerInstance timerInstance =
        workflowState.getTimerState().get(elementInstanceKey, record.getKey());
    if (timerInstance == null) {
      streamWriter.appendRejection(
          record, RejectionType.NOT_FOUND, String.format(NO_TIMER_FOUND_MESSAGE, record.getKey()));
      return;
    }
    workflowState.getTimerState().remove(timerInstance);

    processTimerTrigger(record, streamWriter, timer, elementInstanceKey);
  }

  private void processTimerTrigger(
      final TypedRecord<TimerRecord> record,
      final TypedStreamWriter streamWriter,
      final TimerRecord timer,
      final long elementInstanceKey) {

    final var workflowKey = timer.getWorkflowKey();
    final var catchEvent =
        workflowState.getFlowElement(
            workflowKey, timer.getTargetElementIdBuffer(), ExecutableCatchEvent.class);

    final boolean isTriggered =
        triggerEvent(streamWriter, timer, elementInstanceKey, workflowKey, catchEvent);

    if (isTriggered) {
      streamWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);

      // todo(npepinpe): migrate to bpmn step processor
      if (shouldReschedule(timer)) {
        rescheduleTimer(timer, streamWriter, catchEvent);
      }
    } else {
      streamWriter.appendRejection(
          record,
          RejectionType.INVALID_STATE,
          String.format(NO_ACTIVE_TIMER_MESSAGE, record.getKey()));
    }
  }

  private boolean triggerEvent(
      final TypedStreamWriter streamWriter,
      final TimerRecord timer,
      final long elementInstanceKey,
      final long workflowKey,
      final ExecutableCatchEvent catchEvent) {

    if (elementInstanceKey > 0) {
      final var elementInstance =
          workflowState.getElementInstanceState().getInstance(elementInstanceKey);

      return eventHandle.triggerEvent(streamWriter, elementInstance, catchEvent, NO_VARIABLES);

    } else {
      final var workflowInstanceKey =
          eventHandle.triggerStartEvent(
              streamWriter, workflowKey, timer.getTargetElementIdBuffer(), NO_VARIABLES);

      return workflowInstanceKey > 0;
    }
  }

  private boolean shouldReschedule(final TimerRecord timer) {
    return timer.getRepetitions() == RepeatingInterval.INFINITE || timer.getRepetitions() > 1;
  }

  private void rescheduleTimer(
      final TimerRecord record, final TypedStreamWriter writer, final ExecutableCatchEvent event) {
    final Timer timer;
    try {
      timer = event.getTimerFactory().apply(expressionProcessor, record.getElementInstanceKey());
    } catch (Exception e) {
      final String message =
          String.format(
              "Expected to reschedule repeating timer for element with id '%s', but an exception occurred",
              BufferUtil.bufferAsString(event.getId()));
      throw new IllegalStateException(message, e);
      // todo(#4208): raise incident instead of throwing an exception
    }

    int repetitions = record.getRepetitions();
    if (repetitions != RepeatingInterval.INFINITE) {
      repetitions--;
    }

    final Interval interval = timer.getInterval();
    final Timer repeatingInterval = new RepeatingInterval(repetitions, interval);
    catchEventBehavior.subscribeToTimerEvent(
        record.getElementInstanceKey(),
        record.getWorkflowInstanceKey(),
        record.getWorkflowKey(),
        event.getId(),
        repeatingInterval,
        writer);
  }
}
