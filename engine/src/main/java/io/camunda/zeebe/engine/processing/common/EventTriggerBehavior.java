/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceLifecycle;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public class EventTriggerBehavior {

  private final ProcessInstanceRecord eventRecord = new ProcessInstanceRecord();
  private final ProcessEventRecord processEventRecord = new ProcessEventRecord();

  private final KeyGenerator keyGenerator;
  private final CatchEventBehavior catchEventBehavior;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final ElementInstanceState elementInstanceState;
  private final EventScopeInstanceState eventScopeInstanceState;

  private final VariableBehavior variableBehavior;

  public EventTriggerBehavior(
      final KeyGenerator keyGenerator,
      final CatchEventBehavior catchEventBehavior,
      final Writers writers,
      final ZeebeState zeebeState) {
    this.keyGenerator = keyGenerator;
    this.catchEventBehavior = catchEventBehavior;
    commandWriter = writers.command();
    stateWriter = writers.state();

    elementInstanceState = zeebeState.getElementInstanceState();
    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();

    variableBehavior =
        new VariableBehavior(zeebeState.getVariableState(), writers.state(), keyGenerator);
  }

  public void triggerEventSubProcess(
      final ExecutableStartEvent startEvent,
      final long flowScopeElementInstanceKey,
      final ProcessInstanceRecord recordValue,
      final DirectBuffer variables) {

    final var flowScopeElementInstance =
        elementInstanceState.getInstance(flowScopeElementInstanceKey);

    if (flowScopeElementInstance.isInterrupted()
        && !flowScopeElementInstance
            .getInterruptingElementId()
            .equals(startEvent.getEventSubProcess())) {
      // the flow scope is already interrupted - discard this event
      return;
    }

    final var flowScopeContext =
        new BpmnElementContextImpl()
            .copy(
                flowScopeElementInstanceKey,
                flowScopeElementInstance.getValue(),
                flowScopeElementInstance.getState());

    final var eventTrigger = eventScopeInstanceState.peekEventTrigger(flowScopeElementInstanceKey);
    if (eventTrigger == null) {
      // the activity (i.e. its event scope) is left - discard the event
      return;
    }

    if (startEvent.interrupting()) {
      catchEventBehavior.unsubscribeEventSubprocesses(flowScopeContext);

      final var noActiveChildInstances = terminateChildInstances(flowScopeContext);
      if (!noActiveChildInstances) {
        // activation of event sub process happens in flow scope of last child terminated
        return;
      }
    }

    activateTriggeredEvent(
        eventTrigger.getEventKey(),
        startEvent,
        flowScopeElementInstanceKey,
        flowScopeElementInstanceKey,
        recordValue,
        variables);
  }

  private boolean terminateChildInstances(final BpmnElementContext flowScopeContext) {
    // we need to go to the parent and delete all children to trigger the interrupting event sub
    // process
    elementInstanceState.getChildren(flowScopeContext.getElementInstanceKey()).stream()
        .filter(child -> ProcessInstanceLifecycle.canTerminate(child.getState()))
        .map(
            childInstance ->
                flowScopeContext.copy(
                    childInstance.getKey(), childInstance.getValue(), childInstance.getState()))
        .forEach(
            childInstanceContext ->
                commandWriter.appendFollowUpCommand(
                    childInstanceContext.getElementInstanceKey(),
                    ProcessInstanceIntent.TERMINATE_ELEMENT,
                    childInstanceContext.getRecordValue()));

    final var elementInstance =
        elementInstanceState.getInstance(flowScopeContext.getElementInstanceKey());
    final var activeChildInstances = elementInstance.getNumberOfActiveElementInstances();

    return activeChildInstances == 0;
  }

  /**
   * Triggers a process by updating the state with a new {@link ProcessEventIntent#TRIGGERING}
   * event.
   *
   * <p>NOTE: this method assumes that the caller already verified that the target can accept new
   * events!
   *
   * @param processDefinitionKey the event's corresponding process definition key
   * @param processInstanceKey the event's corresponding process instance key
   * @param eventScopeKey the event's scope key, which used to index the trigger in {@link
   *     io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState}
   * @param catchEventId the ID of the element which should be triggered by the event
   * @param variables the variables/payload of the event (can be empty)
   * @return the key of the process event
   */
  public long triggeringProcessEvent(
      final long processDefinitionKey,
      final long processInstanceKey,
      final long eventScopeKey,
      final DirectBuffer catchEventId,
      final DirectBuffer variables) {
    final var eventKey = keyGenerator.nextKey();
    processEventRecord.reset();
    processEventRecord
        .setScopeKey(eventScopeKey)
        .setTargetElementIdBuffer(catchEventId)
        .setVariablesBuffer(variables)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey);
    stateWriter.appendFollowUpEvent(eventKey, ProcessEventIntent.TRIGGERING, processEventRecord);
    return eventKey;
  }

  /**
   * Marks a process to be triggered by updating the state with a new {@link
   * ProcessEventIntent#TRIGGERED} event.
   *
   * @param processInstanceKey the process instance key of the event trigger
   * @param processDefinitionKey the process instance key of the event trigger
   * @param eventScopeKey the event's scope key, which is used as identifier for the event trigger
   * @param catchEventId the ID of the element which was triggered by the event
   */
  public void processEventTriggered(
      final long eventTriggerKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final long eventScopeKey,
      final DirectBuffer catchEventId) {
    processEventRecord.reset();
    processEventRecord
        .setScopeKey(eventScopeKey)
        .setTargetElementIdBuffer(catchEventId)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey);
    stateWriter.appendFollowUpEvent(
        eventTriggerKey, ProcessEventIntent.TRIGGERED, processEventRecord);
  }

  public void activateTriggeredEvent(
      final long processEventKey,
      final ExecutableFlowElement triggeredEvent,
      final long eventScopeKey,
      final long flowScopeKey,
      final ProcessInstanceRecord elementRecord,
      final DirectBuffer variables) {

    eventRecord.reset();
    eventRecord.wrap(elementRecord);
    eventRecord.setFlowScopeKey(flowScopeKey);

    final var flowScope = triggeredEvent.getFlowScope();
    if (flowScope == null) {
      throw new IllegalStateException(
          "Expected to activate triggered event, but flow scope is null");
    }

    // We need to activate the event sub process without writing a Triggered event for the process
    // event. The reason for this is that the event trigger contains variables that are required
    // for applying the variable output mappings of the start event. Writing the triggered event
    // would delete the event trigger, causing us to lose access to these variables.
    if (flowScope.getElementType() == BpmnElementType.EVENT_SUB_PROCESS
        && triggeredEvent.getElementType() == BpmnElementType.START_EVENT) {
      activateEventSubProcess(flowScope);
      return;
    }

    processEventTriggered(
        processEventKey,
        elementRecord.getProcessDefinitionKey(),
        elementRecord.getProcessInstanceKey(),
        eventScopeKey,
        triggeredEvent.getId());

    eventRecord
        .setBpmnElementType(triggeredEvent.getElementType())
        .setElementId(triggeredEvent.getId());

    final var eventInstanceKey = keyGenerator.nextKey();
    // transition to activating and activated directly to pass the variables to this instance
    stateWriter.appendFollowUpEvent(
        eventInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, eventRecord);
    stateWriter.appendFollowUpEvent(
        eventInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, eventRecord);

    if (variables.capacity() > 0) {
      // set as local variables of the element instance to use them for the variable output
      // mapping
      variableBehavior.mergeLocalDocument(
          eventInstanceKey,
          elementRecord.getProcessDefinitionKey(),
          elementRecord.getProcessInstanceKey(),
          elementRecord.getBpmnProcessIdBuffer(),
          variables);
    }

    commandWriter.appendFollowUpCommand(
        eventInstanceKey, ProcessInstanceIntent.COMPLETE_ELEMENT, eventRecord);
  }

  private void activateEventSubProcess(final ExecutableFlowElement flowScope) {
    eventRecord
        .setBpmnElementType(BpmnElementType.EVENT_SUB_PROCESS)
        .setElementId(flowScope.getId());
    commandWriter.appendNewCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, eventRecord);
  }
}
