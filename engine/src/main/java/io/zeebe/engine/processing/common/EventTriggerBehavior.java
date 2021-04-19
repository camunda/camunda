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
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class EventTriggerBehavior {
  private final ProcessInstanceRecord eventRecord = new ProcessInstanceRecord();

  private final KeyGenerator keyGenerator;
  private final CatchEventBehavior catchEventBehavior;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableVariableState variablesState;

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
    variablesState = zeebeState.getVariableState();

    variableBehavior = new VariableBehavior(variablesState, writers.state(), keyGenerator);
  }

  private ProcessInstanceRecord getEventRecord(
      final ProcessInstanceRecord value,
      final EventTrigger event,
      final BpmnElementType elementType) {
    eventRecord.reset();
    eventRecord.wrap(value);
    eventRecord.setElementId(event.getElementId());
    eventRecord.setBpmnElementType(elementType);

    return eventRecord;
  }

  public void unsubscribeFromEvents(final BpmnElementContext context) {
    final var sideEffectQueue = new SideEffectQueue();
    catchEventBehavior.unsubscribeFromEvents(context, commandWriter, sideEffectQueue);

    // side effect can immediately executed, since on restart we not reprocess anymore the commands
    sideEffectQueue.flush();
  }

  private void publishActivatingEvent(
      final long elementInstanceKey, final ProcessInstanceRecord eventRecord) {

    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, eventRecord);
  }

  private void deferActivatingEvent(
      final long flowScopeElementInstanceKey,
      final long eventElementInstanceKey,
      final ProcessInstanceRecord record) {

    elementInstanceState.storeRecord(
        eventElementInstanceKey,
        flowScopeElementInstanceKey,
        record,
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        Purpose.DEFERRED);
  }

  public void triggerEventSubProcess(
      final ExecutableStartEvent startEvent,
      final long flowScopeElementInstanceKey,
      final ProcessInstanceRecord recordValue) {

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

    triggerEvent(
        flowScopeContext,
        eventTrigger -> {
          final var eventSubProcessElementId = startEvent.getEventSubProcess();
          final var record =
              getEventRecord(recordValue, eventTrigger, BpmnElementType.EVENT_SUB_PROCESS)
                  .setFlowScopeKey(flowScopeElementInstanceKey)
                  .setElementId(eventSubProcessElementId);

          final long eventElementInstanceKey = keyGenerator.nextKey();
          if (startEvent.interrupting()) {

            triggerInterruptingEventSubProcess(flowScopeContext, record, eventElementInstanceKey);

          } else {
            // activate non-interrupting event sub-process
            publishActivatingEvent(eventElementInstanceKey, record);
          }

          return eventElementInstanceKey;
        });
  }

  private void triggerInterruptingEventSubProcess(
      final BpmnElementContext flowScopeContext,
      final ProcessInstanceRecord eventRecord,
      final long eventElementInstanceKey) {

    unsubscribeFromEvents(flowScopeContext);

    final var noActiveChildInstances = terminateChildInstances(flowScopeContext);
    if (noActiveChildInstances) {
      // activate interrupting event sub-process
      publishActivatingEvent(eventElementInstanceKey, eventRecord);

    } else {
      // wait until child instances are terminated
      deferActivatingEvent(
          flowScopeContext.getElementInstanceKey(), eventElementInstanceKey, eventRecord);
    }
  }

  private boolean terminateChildInstances(final BpmnElementContext flowScopeContext) {
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

      } else if (childInstanceContext.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED) {
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

  public void triggerEvent(
      final BpmnElementContext context, final ToLongFunction<EventTrigger> eventHandler) {

    final var eventTrigger =
        eventScopeInstanceState.peekEventTrigger(context.getElementInstanceKey());

    if (eventTrigger == null) {
      // the activity (i.e. its event scope) is left - discard the event
      return;
    }

    final var eventElementInstanceKey = eventHandler.applyAsLong(eventTrigger);

    final var eventVariables = eventTrigger.getVariables();
    if (eventElementInstanceKey > 0 && eventVariables != null && eventVariables.capacity() > 0) {
      variablesState.setTemporaryVariables(eventElementInstanceKey, eventVariables);
    }
  }

  public void activateTriggeredEvent(
      final ExecutableFlowElement triggeredEvent,
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

    if (flowScope.getElementType() == BpmnElementType.EVENT_SUB_PROCESS) {

      // first activate the event sub process
      eventRecord
          .setBpmnElementType(BpmnElementType.EVENT_SUB_PROCESS)
          .setElementId(flowScope.getId());

      final var eventSubProcessKey = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(
          eventSubProcessKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, eventRecord);

      // event sub process is not yet migrated
      // todo(#6196): on migration of event sub process we want immediately move activated for the
      // event sub process
      //      stateWriter.appendFollowUpEvent(
      //          eventSubProcessKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, eventRecord);
      //      eventRecord.setFlowScopeKey(eventSubProcessKey);

      // event sub process will activate start event
      return; // todo(#6196) remove this line after migrating event sub process
      // now we can trigger the start event
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
}
