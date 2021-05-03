/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.common.EventHandle;
import io.zeebe.engine.processing.common.EventTriggerBehavior;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.analyzers.CatchEventAnalyzer;
import io.zeebe.engine.state.analyzers.CatchEventAnalyzer.CatchEventTuple;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BpmnEventPublicationBehavior {
  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final ElementInstanceState elementInstanceState;
  private final EventHandle eventHandle;
  private final CatchEventAnalyzer catchEventAnalyzer;

  public BpmnEventPublicationBehavior(
      final MutableZeebeState zeebeState,
      final EventTriggerBehavior eventTriggerBehavior,
      final Writers writers) {
    elementInstanceState = zeebeState.getElementInstanceState();
    eventHandle =
        new EventHandle(
            zeebeState.getKeyGenerator(),
            zeebeState.getEventScopeInstanceState(),
            writers,
            zeebeState.getProcessState(),
            eventTriggerBehavior);
    catchEventAnalyzer = new CatchEventAnalyzer(zeebeState.getProcessState(), elementInstanceState);
  }

  /**
   * Throws an error event to the given element instance/catch event pair. Only throws the event if
   * the given element instance is exists and is accepting events, e.g. isn't terminating, wasn't
   * interrupted, etc.
   *
   * @param catchEventTuple a tuple representing a catch event and its current instance
   */
  public void throwErrorEvent(final CatchEventAnalyzer.CatchEventTuple catchEventTuple) {
    final ElementInstance eventScopeInstance = catchEventTuple.getElementInstance();
    final ExecutableCatchEvent catchEvent = catchEventTuple.getCatchEvent();

    if (eventHandle.canTriggerElement(eventScopeInstance)) {
      eventHandle.activateElement(
          catchEvent, eventScopeInstance.getKey(), eventScopeInstance.getValue());
    }
  }

  /**
   * Finds the right catch event for the given error. This is done by going up through the scope
   * hierarchy recursively until a matching catch event is found. If none are found, a failure is
   * returned.
   *
   * <p>The returned {@link CatchEventTuple} can be used to throw the event via {@link
   * #throwErrorEvent(CatchEventTuple)}.
   *
   * @param errorCode the error code of the error event
   * @param context the current element context
   * @return a valid {@link CatchEventTuple} if a catch event is found, or a failure otherwise
   */
  public Either<Failure, CatchEventTuple> findErrorCatchEvent(
      final DirectBuffer errorCode, final BpmnElementContext context) {
    final var flowScopeInstance = elementInstanceState.getInstance(context.getFlowScopeKey());
    final CatchEventTuple catchEvent =
        catchEventAnalyzer.findCatchEvent(errorCode, flowScopeInstance);

    if (catchEvent != null) {
      return Either.right(catchEvent);
    }

    final var errorMessage =
        String.format(
            "Expected to throw an error event with the code '%s', but it was not caught.",
            bufferAsString(errorCode));
    final var failure = new Failure(errorMessage, ErrorType.UNHANDLED_ERROR_EVENT);
    return Either.left(failure);
  }
}
