/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.timer;

import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.EventHandle;
import io.zeebe.engine.processing.common.EventTriggerBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.model.bpmn.util.time.Interval;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.util.Either;
import io.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;
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
  private final EventScopeInstanceState eventScopeInstanceState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final EventHandle eventHandle;

  public TriggerTimerProcessor(
      final MutableZeebeState zeebeState,
      final CatchEventBehavior catchEventBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final ExpressionProcessor expressionProcessor,
      final Writers writers) {
    this.catchEventBehavior = catchEventBehavior;
    this.expressionProcessor = expressionProcessor;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();

    processState = zeebeState.getProcessState();
    elementInstanceState = zeebeState.getElementInstanceState();
    timerInstanceState = zeebeState.getTimerState();
    keyGenerator = zeebeState.getKeyGenerator();
    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();
    eventHandle =
        new EventHandle(
            keyGenerator,
            zeebeState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior);
  }

  @Override
  public void processRecord(
      final TypedRecord<TimerRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffects) {
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
      if (!eventScopeInstanceState.isAcceptingEvent(processDefinitionKey)) {
        rejectNoActiveTimer(record);
        return;
      }

      stateWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);
      final long processInstanceKey = keyGenerator.nextKey();
      eventHandle.activateProcessInstanceForStartEvent(
          processDefinitionKey, processInstanceKey, timer.getTargetElementIdBuffer(), NO_VARIABLES);
    } else {
      final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
      if (!eventHandle.canTriggerElement(elementInstance)) {
        rejectNoActiveTimer(record);
        return;
      }

      stateWriter.appendFollowUpEvent(record.getKey(), TimerIntent.TRIGGERED, timer);
      eventHandle.activateElement(catchEvent, elementInstanceKey, elementInstance.getValue());
    }

    if (shouldReschedule(timer)) {
      rescheduleTimer(timer, catchEvent, streamWriter, sideEffects);
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

  private void rescheduleTimer(
      final TimerRecord record,
      final ExecutableCatchEvent event,
      final TypedCommandWriter writer,
      final Consumer<SideEffectProducer> sideEffects) {
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

    int repetitions = record.getRepetitions();
    if (repetitions != RepeatingInterval.INFINITE) {
      repetitions--;
    }

    final Interval interval = timer.map(Timer::getInterval).get();
    final Timer repeatingInterval = new RepeatingInterval(repetitions, interval);
    catchEventBehavior.subscribeToTimerEvent(
        record.getElementInstanceKey(),
        record.getProcessInstanceKey(),
        record.getProcessDefinitionKey(),
        event.getId(),
        repeatingInterval,
        writer,
        sideEffects::accept);
  }
}
