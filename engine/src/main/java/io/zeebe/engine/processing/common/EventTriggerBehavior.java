/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.common;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.ProcessInstanceLifecycle;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.processing.variable.VariableBehavior;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessEventIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class EventTriggerBehavior {

  private static final String ERROR_MSG_EXPECTED_START_EVENT =
      "Expected an start event to be triggered on EventSubProcess scope, but was %s";
  private final ProcessInstanceRecord eventRecord = new ProcessInstanceRecord();
  private final ProcessEventRecord processEventRecord = new ProcessEventRecord();

  private final KeyGenerator keyGenerator;
  private final CatchEventBehavior catchEventBehavior;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  private final VariableBehavior variableBehavior;

  public EventTriggerBehavior(
      final KeyGenerator keyGenerator,
      final CatchEventBehavior catchEventBehavior,
      final Writers writers,
      final MutableZeebeState zeebeState) {
    this.keyGenerator = keyGenerator;
    this.catchEventBehavior = catchEventBehavior;
    commandWriter = writers.command();
    stateWriter = writers.state();

    elementInstanceState = zeebeState.getElementInstanceState();
    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();

    variableBehavior =
        new VariableBehavior(zeebeState.getVariableState(), writers.state(), keyGenerator);
  }

  public void unsubscribeFromEvents(final BpmnElementContext context) {
    final var sideEffectQueue = new SideEffectQueue();
    catchEventBehavior.unsubscribeFromEvents(context, commandWriter, sideEffectQueue);

    // side effect can immediately executed, since on restart we not reprocess anymore the commands
    sideEffectQueue.flush();
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
      unsubscribeFromEvents(flowScopeContext);

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
    // we need to go to the parent and delete all childs to trigger the interrupting event sub
    // process
    final var childInstances =
        elementInstanceState.getChildren(flowScopeContext.getElementInstanceKey()).stream()
            .map(
                childInstance ->
                    flowScopeContext.copy(
                        childInstance.getKey(), childInstance.getValue(), childInstance.getState()))
            .collect(Collectors.toList());

    for (final BpmnElementContext childInstanceContext : childInstances) {

      if (ProcessInstanceLifecycle.canTerminate(childInstanceContext.getIntent())) {
        if (!MigratedStreamProcessors.isMigrated(childInstanceContext.getBpmnElementType())) {
          transitionToTerminating(childInstanceContext);
        } else {
          commandWriter.appendFollowUpCommand(
              childInstanceContext.getElementInstanceKey(),
              ProcessInstanceIntent.TERMINATE_ELEMENT,
              childInstanceContext.getRecordValue());
        }

      } else if (!MigratedStreamProcessors.isMigrated(childInstanceContext.getBpmnElementType())
          && childInstanceContext.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED) {
        // clean up the state because the completed event will not be processed
        eventScopeInstanceState.deleteInstance(childInstanceContext.getElementInstanceKey());
        elementInstanceState.removeInstance(childInstanceContext.getElementInstanceKey());
      }
    }

    final var elementInstance =
        elementInstanceState.getInstance(flowScopeContext.getElementInstanceKey());
    final var activeChildInstances = elementInstance.getNumberOfActiveElementInstances();

    return activeChildInstances == 0;
  }

  // https://github.com/camunda-cloud/zeebe/issues/6202
  // todo(zell): should be removed or replaced -
  // it is currently duplication of BpmnTransitionBehavior
  // ideally this class/behavior will be merged with others
  public BpmnElementContext transitionToTerminating(final BpmnElementContext context) {
    final var transitionedContext =
        transitionTo(context, ProcessInstanceIntent.ELEMENT_TERMINATING);
    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      registerStateTransition(context, ProcessInstanceIntent.ELEMENT_TERMINATING);
    }
    return transitionedContext;
  }

  public void registerStateTransition(
      final BpmnElementContext context, final ProcessInstanceIntent newState) {
    switch (newState) {
      case ELEMENT_ACTIVATING:
      case ELEMENT_ACTIVATED:
      case ELEMENT_COMPLETING:
      case ELEMENT_COMPLETED:
      case ELEMENT_TERMINATING:
      case ELEMENT_TERMINATED:
        updateElementInstanceState(context, newState);
        break;

      default:
        // other transitions doesn't change the state of an element instance
        break;
    }
  }

  private void updateElementInstanceState(
      final BpmnElementContext context, final ProcessInstanceIntent newState) {
    elementInstanceState.updateInstance(
        context.getElementInstanceKey(), elementInstance -> elementInstance.setState(newState));
  }

  private void verifyTransition(
      final BpmnElementContext context, final ProcessInstanceIntent transition) {

    if (!ProcessInstanceLifecycle.canTransition(context.getIntent(), transition)) {
      throw new BpmnProcessingException(
          context,
          String.format(
              "Expected to take transition to '%s' but element instance is in state '%s'.",
              transition, context.getIntent()));
    }
  }

  private BpmnElementContext transitionTo(
      final BpmnElementContext context, final ProcessInstanceIntent transition) {
    final var key = context.getElementInstanceKey();
    final var value = context.getRecordValue();
    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      verifyTransition(context, transition);
      stateWriter.appendFollowUpEvent(key, transition, value);
    } else {
      stateWriter.appendFollowUpEvent(key, transition, value);
    }
    return context.copy(key, value, transition);
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
   *     io.zeebe.engine.state.immutable.EventScopeInstanceState}
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
   * @param processDefinitionKey the process instance key of the event trigger
   * @param processInstanceKey the process instance key of the event trigger
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

    processEventTriggered(
        processEventKey,
        elementRecord.getProcessDefinitionKey(),
        elementRecord.getProcessInstanceKey(),
        eventScopeKey,
        triggeredEvent.getId());

    if (flowScope.getElementType() == BpmnElementType.EVENT_SUB_PROCESS) {
      if (triggeredEvent instanceof ExecutableStartEvent) {
        activateEventSubProcess((ExecutableStartEvent) triggeredEvent, flowScope);
        return;
      } else {
        throw new IllegalStateException(
            String.format(ERROR_MSG_EXPECTED_START_EVENT, triggeredEvent.getClass()));
      }
    }

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
          variables);
    }

    commandWriter.appendFollowUpCommand(
        eventInstanceKey, ProcessInstanceIntent.COMPLETE_ELEMENT, eventRecord);
  }

  private void activateEventSubProcess(
      final ExecutableStartEvent triggeredStartEvent, final ExecutableFlowElement flowScope) {
    // First we move the event sub process immediately to ACTIVATED,
    // to make sure that we can copy the temp variables from the flow scope directly to the
    // event sub process scope. This is done in the ACTIVATING applier of the event sub process.
    eventRecord
        .setBpmnElementType(BpmnElementType.EVENT_SUB_PROCESS)
        .setElementId(flowScope.getId());

    final var eventSubProcessKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        eventSubProcessKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, eventRecord);
    stateWriter.appendFollowUpEvent(
        eventSubProcessKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, eventRecord);

    // Then we ACTIVATE the start event and copy the temporary variables further down, such that
    // we can apply the output mappings.
    eventRecord
        .setFlowScopeKey(eventSubProcessKey)
        .setBpmnElementType(triggeredStartEvent.getElementType())
        .setElementId(triggeredStartEvent.getId());

    commandWriter.appendNewCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, eventRecord);
  }
}
