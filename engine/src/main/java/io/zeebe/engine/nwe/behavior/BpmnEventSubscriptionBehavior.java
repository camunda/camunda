/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe.behavior;

import io.zeebe.engine.nwe.BpmnElementContext;
import io.zeebe.engine.processor.Failure;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.ExpressionProcessor.EvaluationException;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableBoundaryEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventSupplier;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyException;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.Either;
import io.zeebe.util.buffer.BufferUtil;

public final class BpmnEventSubscriptionBehavior {

  private final WorkflowInstanceRecord eventRecord = new WorkflowInstanceRecord();

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final ElementInstanceState elementInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final TypedStreamWriter streamWriter;
  private final KeyGenerator keyGenerator;

  public BpmnEventSubscriptionBehavior(
      final BpmnStateBehavior stateBehavior,
      final BpmnStateTransitionBehavior stateTransitionBehavior,
      final CatchEventBehavior catchEventBehavior,
      final TypedStreamWriter streamWriter,
      final ZeebeState zeebeState) {
    this.stateBehavior = stateBehavior;
    this.stateTransitionBehavior = stateTransitionBehavior;
    this.catchEventBehavior = catchEventBehavior;
    this.streamWriter = streamWriter;

    eventScopeInstanceState = zeebeState.getWorkflowState().getEventScopeInstanceState();
    elementInstanceState = zeebeState.getWorkflowState().getElementInstanceState();
    keyGenerator = zeebeState.getKeyGenerator();
  }

  public <T extends ExecutableActivity> void triggerBoundaryEvent(
      final T element, final BpmnElementContext context) {
    final var eventTrigger =
        eventScopeInstanceState.peekEventTrigger(context.getElementInstanceKey());

    if (eventTrigger == null) {
      // the activity (i.e. its event scope) is left - discard the event
      return;
    }

    eventRecord.reset();
    eventRecord.wrap(context.getRecordValue());
    eventRecord.setElementId(eventTrigger.getElementId());
    eventRecord.setBpmnElementType(BpmnElementType.BOUNDARY_EVENT);

    final var boundaryEvent = getBoundaryEvent(element, context, eventTrigger);

    final long boundaryElementInstanceKey = keyGenerator.nextKey();
    if (boundaryEvent.interrupting()) {

      deferBoundaryEvent(context, boundaryElementInstanceKey);

      stateTransitionBehavior.transitionToTerminating(context);

    } else {
      activateBoundaryEvent(context, boundaryElementInstanceKey, eventRecord);
    }

    stateBehavior
        .getVariablesState()
        .setTemporaryVariables(boundaryElementInstanceKey, eventTrigger.getVariables());

    eventScopeInstanceState.deleteTrigger(
        context.getElementInstanceKey(), eventTrigger.getEventKey());
  }

  private <T extends ExecutableActivity> ExecutableBoundaryEvent getBoundaryEvent(
      final T element, final BpmnElementContext context, final EventTrigger eventTrigger) {

    return element.getBoundaryEvents().stream()
        .filter(boundaryEvent -> boundaryEvent.getId().equals(eventTrigger.getElementId()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Expected boundary event with id '%s' but not found. [context: %s]",
                        BufferUtil.bufferAsString(eventTrigger.getElementId()), context)));
  }

  private void deferBoundaryEvent(
      final BpmnElementContext context, final long boundaryElementInstanceKey) {

    elementInstanceState.storeRecord(
        boundaryElementInstanceKey,
        context.getElementInstanceKey(),
        eventRecord,
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        Purpose.DEFERRED);
  }

  public void publishTriggeredBoundaryEvent(final BpmnElementContext context) {

    elementInstanceState.getDeferredRecords(context.getElementInstanceKey()).stream()
        .filter(record -> record.getValue().getBpmnElementType() == BpmnElementType.BOUNDARY_EVENT)
        .findFirst()
        .ifPresent(
            deferredRecord ->
                activateBoundaryEvent(context, deferredRecord.getKey(), deferredRecord.getValue()));
  }

  private void activateBoundaryEvent(
      final BpmnElementContext context,
      final long elementInstanceKey,
      final WorkflowInstanceRecord eventRecord) {

    streamWriter.appendNewEvent(
        elementInstanceKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, eventRecord);

    stateBehavior.createElementInstanceInFlowScope(context, elementInstanceKey, eventRecord);
    stateBehavior.spawnToken(context);
  }

  public <T extends ExecutableCatchEventSupplier> Either<Failure, Void> subscribeToEvents(
      final T element, final BpmnElementContext context) {

    try {
      catchEventBehavior.subscribeToEvents(context.toStepContext(), element);
      return Either.right(null);

    } catch (final MessageCorrelationKeyException e) {
      return Either.left(
          new Failure(
              e.getMessage(),
              ErrorType.EXTRACT_VALUE_ERROR,
              e.getContext().getVariablesScopeKey()));

    } catch (final EvaluationException e) {
      return Either.left(
          new Failure(
              e.getMessage(), ErrorType.EXTRACT_VALUE_ERROR, context.getElementInstanceKey()));
    }
  }

  public void unsubscribeFromEvents(final BpmnElementContext context) {
    catchEventBehavior.unsubscribeFromEvents(
        context.getElementInstanceKey(), context.toStepContext());
  }

  public void publishTriggeredEventSubProcess(final BpmnElementContext context) {
    final var elementInstance = stateBehavior.getElementInstance(context);

    if (isInterrupted(elementInstance)) {
      elementInstanceState.getDeferredRecords(context.getElementInstanceKey()).stream()
          .filter(record -> record.getKey() == elementInstance.getInterruptingEventKey())
          .filter(record -> record.getValue().getBpmnElementType() == BpmnElementType.SUB_PROCESS)
          .findFirst()
          .ifPresent(
              record -> {
                final var elementInstanceKey = record.getKey();
                final var interruptingRecord = record.getValue();

                streamWriter.appendNewEvent(
                    elementInstanceKey,
                    WorkflowInstanceIntent.ELEMENT_ACTIVATING,
                    interruptingRecord);

                stateBehavior.createChildElementInstance(
                    context, elementInstanceKey, interruptingRecord);
              });
    }
  }

  private boolean isInterrupted(final ElementInstance elementInstance) {
    return elementInstance.getNumberOfActiveTokens() == 2
        && elementInstance.isInterrupted()
        && elementInstance.isActive();
  }
}
