/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

/**
 * Checks the event trigger, and if it is related to the element itself, will transition to
 * completing.
 */
public class EventOccurredHandler<T extends ExecutableFlowElement> extends AbstractHandler<T> {
  private final WorkflowInstanceRecord eventRecord = new WorkflowInstanceRecord();

  public EventOccurredHandler() {
    this(WorkflowInstanceIntent.ELEMENT_COMPLETING);
  }

  public EventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    return true;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && (!hasWorkflowInstance(context) || isElementActive(context.getElementInstance()));
  }

  /**
   * Returns the latest event trigger but does not consume it from the state. It will be consumed
   * once the caller calls {@link #processEventTrigger(BpmnStepContext, long, long, EventTrigger)}.
   */
  protected EventTrigger getTriggeredEvent(BpmnStepContext<T> context, long scopeKey) {
    return context.getStateDb().getEventScopeInstanceState().peekEventTrigger(scopeKey);
  }

  /**
   * Will trigger the creation of a new instance of the flow with the same elementId as the event,
   * but in an asynchronous fashion in the form of a deferred record.
   *
   * @param context the current BPMN context
   * @param eventScopeKey the event scope key from which the event trigger was initially read
   * @param flowScopeKey the key of the element instance which will publish the deferred record
   * @param record the record to defer
   * @param event the event trigger to handle
   */
  protected long deferEvent(
      BpmnStepContext<T> context,
      long eventScopeKey,
      long flowScopeKey,
      WorkflowInstanceRecord record,
      EventTrigger event) {
    final long eventInstanceKey =
        context
            .getOutput()
            .deferRecord(flowScopeKey, record, WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    processEventTrigger(context, eventScopeKey, eventInstanceKey, event);
    return eventInstanceKey;
  }

  /**
   * Will trigger the creation of a new instance of the flow with the same elementId as the event in
   * a synchronous fashion by publishing its ELEMENT_ACTIVATING event and spawning a new token for
   * it.
   *
   * @param context the current BPMN context
   * @param eventScopeKey the event scope key from which the event trigger was initially read
   * @param record the record to publish
   * @param event the event trigger to handle
   */
  protected long publishEvent(
      BpmnStepContext<T> context,
      long eventScopeKey,
      WorkflowInstanceRecord record,
      EventTrigger event) {
    final long eventInstanceKey =
        context.getOutput().appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, record);
    processEventTrigger(context, eventScopeKey, eventInstanceKey, event);
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    flowScopeInstance.spawnToken();
    context.getStateDb().getElementInstanceState().updateInstance(flowScopeInstance);

    return eventInstanceKey;
  }

  /**
   * Applies the event variables to the given variable scope, and removes the event from the state
   * ensuring it cannot be reprocessed.
   *
   * @param context the current BPMN context
   * @param eventScopeKey the event scope key from which the event trigger was initially read
   * @param variableScopeKey the variable scope key on which the variables of the event trigger will
   *     be set
   * @param event the event trigger to handle
   */
  protected void processEventTrigger(
      BpmnStepContext<T> context, long eventScopeKey, long variableScopeKey, EventTrigger event) {
    context
        .getElementInstanceState()
        .getVariablesState()
        .setTemporaryVariables(variableScopeKey, event.getVariables());

    context
        .getStateDb()
        .getEventScopeInstanceState()
        .deleteTrigger(eventScopeKey, event.getEventKey());
  }

  protected WorkflowInstanceRecord getEventRecord(
      BpmnStepContext<T> context, EventTrigger event, BpmnElementType elementType) {
    eventRecord.reset();
    eventRecord.wrap(context.getValue());
    eventRecord.setElementId(event.getElementId());
    eventRecord.setBpmnElementType(elementType);

    return eventRecord;
  }

  /**
   * Timer/Message start events publish an EVENT_OCCURRED event to their respective flow elements,
   * but these are not initially part of a workflow instance.
   */
  private boolean hasWorkflowInstance(BpmnStepContext<T> context) {
    return context.getValue().getWorkflowInstanceKey() >= 0;
  }
}
