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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableReceiveTask;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyException;
import io.zeebe.engine.processor.workflow.message.MessageNameException;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
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

  private static final String NO_WORKFLOW_FOUND_MESSAGE =
      "Expected to create an instance of workflow with key '%d', but no such workflow was found";
  private static final String NO_TRIGGERED_EVENT_MESSAGE =
      "Expected to create an instance of workflow with key '%d', but no triggered event could be found";

  private final WorkflowInstanceRecord eventRecord = new WorkflowInstanceRecord();
  private final WorkflowInstanceRecord recordForWFICreation = new WorkflowInstanceRecord();

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final ElementInstanceState elementInstanceState;
  private final CatchEventBehavior catchEventBehavior;
  private final TypedStreamWriter streamWriter;
  private final KeyGenerator keyGenerator;
  private final WorkflowState workflowState;

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

    workflowState = zeebeState.getWorkflowState();
    eventScopeInstanceState = workflowState.getEventScopeInstanceState();
    elementInstanceState = workflowState.getElementInstanceState();
    keyGenerator = zeebeState.getKeyGenerator();
  }

  public void triggerBoundaryOrIntermediateEvent(
      final ExecutableReceiveTask element, final BpmnElementContext context) {
    final var eventTrigger =
        eventScopeInstanceState.peekEventTrigger(context.getElementInstanceKey());
    if (eventTrigger == null) {
      // no event trigger found, there is nothing to act on
      return;
    }
    final boolean hasEventTriggeredForBoundaryEvent =
        element.getBoundaryEvents().stream()
            .anyMatch(boundaryEvent -> boundaryEvent.getId().equals(eventTrigger.getElementId()));
    if (hasEventTriggeredForBoundaryEvent) {
      triggerBoundaryEvent(element, context, eventTrigger);
    } else {
      triggerIntermediateEvent(context, eventTrigger);
    }
  }

  public void triggerIntermediateEvent(final BpmnElementContext context) {
    final var eventTrigger =
        eventScopeInstanceState.peekEventTrigger(context.getElementInstanceKey());
    triggerIntermediateEvent(context, eventTrigger);
  }

  private void triggerIntermediateEvent(
      final BpmnElementContext context, final EventTrigger eventTrigger) {
    if (eventTrigger == null) {
      // the activity (i.e. its event scope) is left - discard the event
      return;
    }

    stateTransitionBehavior.transitionToCompleting(context);

    stateBehavior
        .getVariablesState()
        .setTemporaryVariables(context.getElementInstanceKey(), eventTrigger.getVariables());

    eventScopeInstanceState.deleteTrigger(
        context.getElementInstanceKey(), eventTrigger.getEventKey());
  }

  public void triggerBoundaryEvent(
      final ExecutableActivity element, final BpmnElementContext context) {
    final var eventTrigger =
        eventScopeInstanceState.peekEventTrigger(context.getElementInstanceKey());
    triggerBoundaryEvent(element, context, eventTrigger);
  }

  private void triggerBoundaryEvent(
      final ExecutableActivity element,
      final BpmnElementContext context,
      final EventTrigger eventTrigger) {
    if (eventTrigger == null) {
      // the activity (i.e. its event scope) is left - discard the event
      return;
    }

    final var record =
        getEventRecord(context.getRecordValue(), eventTrigger, BpmnElementType.BOUNDARY_EVENT);

    final var boundaryEvent = getBoundaryEvent(element, context, eventTrigger);

    final long boundaryElementInstanceKey = keyGenerator.nextKey();
    if (boundaryEvent.interrupting()) {

      deferBoundaryEvent(context, boundaryElementInstanceKey, record);

      stateTransitionBehavior.transitionToTerminating(context);

    } else {
      activateBoundaryEvent(context, boundaryElementInstanceKey, record);
    }

    stateBehavior
        .getVariablesState()
        .setTemporaryVariables(boundaryElementInstanceKey, eventTrigger.getVariables());

    eventScopeInstanceState.deleteTrigger(
        context.getElementInstanceKey(), eventTrigger.getEventKey());
  }

  public void triggerStartEvent(final BpmnElementContext context) {
    final long workflowKey = context.getWorkflowKey();
    final long workflowInstanceKey = context.getWorkflowInstanceKey();

    final var workflow = workflowState.getWorkflowByKey(context.getWorkflowKey());
    if (workflow == null) {
      // this should never happen because workflows are never deleted.
      throw new IllegalStateException(String.format(NO_WORKFLOW_FOUND_MESSAGE, workflowKey));
    }

    final var triggeredEvent = eventScopeInstanceState.peekEventTrigger(workflowKey);
    if (triggeredEvent == null) {
      throw new IllegalStateException(String.format(NO_TRIGGERED_EVENT_MESSAGE, workflowKey));
    }

    createWorkflowInstance(workflow, workflowInstanceKey);

    final var record =
        getEventRecord(context.getRecordValue(), triggeredEvent, BpmnElementType.START_EVENT)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setVersion(workflow.getVersion())
            .setBpmnProcessId(workflow.getBpmnProcessId())
            .setFlowScopeKey(workflowInstanceKey);

    final var newEventInstanceKey = keyGenerator.nextKey();
    elementInstanceState.storeRecord(
        newEventInstanceKey,
        workflowInstanceKey,
        record,
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        Purpose.DEFERRED);

    elementInstanceState
        .getVariablesState()
        .setTemporaryVariables(newEventInstanceKey, triggeredEvent.getVariables());

    eventScopeInstanceState.deleteTrigger(workflowKey, triggeredEvent.getEventKey());
  }

  private void createWorkflowInstance(
      final DeployedWorkflow workflow, final long workflowInstanceKey) {

    recordForWFICreation
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setWorkflowKey(workflow.getKey())
        .setVersion(workflow.getVersion())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setElementId(workflow.getWorkflow().getId())
        .setBpmnElementType(workflow.getWorkflow().getElementType());

    elementInstanceState.newInstance(
        workflowInstanceKey, recordForWFICreation, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    streamWriter.appendFollowUpEvent(
        workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, recordForWFICreation);
  }

  private WorkflowInstanceRecord getEventRecord(
      final WorkflowInstanceRecord value,
      final EventTrigger event,
      final BpmnElementType elementType) {
    eventRecord.reset();
    eventRecord.wrap(value);
    eventRecord.setElementId(event.getElementId());
    eventRecord.setBpmnElementType(elementType);

    return eventRecord;
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
      final BpmnElementContext context,
      final long boundaryElementInstanceKey,
      final WorkflowInstanceRecord record) {

    elementInstanceState.storeRecord(
        boundaryElementInstanceKey,
        context.getElementInstanceKey(),
        record,
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
    } catch (final MessageNameException e) {
      return Either.left(e.getFailure());
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
