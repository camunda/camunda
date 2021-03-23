/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.common;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public final class EventHandle {

  private final ProcessInstanceRecord eventOccurredRecord = new ProcessInstanceRecord();
  private final KeyGenerator keyGenerator;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  // TODO (saig0): use immutable states only (#6200)
  public EventHandle(
      final KeyGenerator keyGenerator,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final Writers writers) {
    this.keyGenerator = keyGenerator;
    this.eventScopeInstanceState = eventScopeInstanceState;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  public boolean canTriggerElement(final ElementInstance eventScopeInstance) {
    return eventScopeInstance != null
        && eventScopeInstance.isActive()
        && eventScopeInstanceState.isAcceptingEvent(eventScopeInstance.getKey());
  }

  public boolean triggerEvent(
      final ElementInstance eventScopeInstance,
      final ExecutableFlowElement catchEvent,
      final DirectBuffer variables) {

    final var canTriggerElement = canTriggerElement(eventScopeInstance);
    if (canTriggerElement) {

      final var eventScopeKey = eventScopeInstance.getKey();
      final var elementRecord = eventScopeInstance.getValue();

      final var newElementInstanceKey = keyGenerator.nextKey();
      eventScopeInstanceState.triggerEvent(
          eventScopeInstance.getKey(), newElementInstanceKey, catchEvent.getId(), variables);

      activateElement(catchEvent, eventScopeKey, elementRecord);
    }

    return canTriggerElement;
  }

  public void activateElement(
      final ExecutableFlowElement catchEvent,
      final long eventScopeKey,
      final ProcessInstanceRecord elementRecord) {

    if (MigratedStreamProcessors.isMigrated(elementRecord.getBpmnElementType())) {

      if (catchEvent.getElementType() == BpmnElementType.INTERMEDIATE_CATCH_EVENT
          || catchEvent.getElementType() == BpmnElementType.RECEIVE_TASK
          || catchEvent.getElementType() == BpmnElementType.EVENT_BASED_GATEWAY) {
        commandWriter.appendFollowUpCommand(
            eventScopeKey, ProcessInstanceIntent.COMPLETE_ELEMENT, elementRecord);

      } else {
        commandWriter.appendNewCommand(ProcessInstanceIntent.ACTIVATE_ELEMENT, elementRecord);
      }

    } else {

      final long eventOccurredKey;

      if (isEventSubprocess(catchEvent)) {
        eventOccurredKey = keyGenerator.nextKey();
        eventOccurredRecord.wrap(elementRecord);
        eventOccurredRecord
            .setElementId(catchEvent.getId())
            .setBpmnElementType(BpmnElementType.START_EVENT)
            .setFlowScopeKey(eventScopeKey);

      } else {
        eventOccurredKey = eventScopeKey;
        eventOccurredRecord.wrap(elementRecord);
      }

      // TODO (saig0): don't write EVENT_OCCURRED when processors are migrated (#6187/#6196)
      stateWriter.appendFollowUpEvent(
          eventOccurredKey, ProcessInstanceIntent.EVENT_OCCURRED, eventOccurredRecord);
    }
  }

  public long triggerStartEvent(
      final long processDefinitionKey, final DirectBuffer elementId, final DirectBuffer variables) {

    final var newElementInstanceKey = keyGenerator.nextKey();
    final var triggered =
        eventScopeInstanceState.triggerEvent(
            processDefinitionKey, newElementInstanceKey, elementId, variables);

    if (triggered) {
      final var processInstanceKey = keyGenerator.nextKey();
      activateStartEvent(processDefinitionKey, processInstanceKey, elementId);
      return processInstanceKey;

    } else {
      return -1L;
    }
  }

  public void activateStartEvent(
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer elementId) {

    final var eventOccurredKey = keyGenerator.nextKey();

    eventOccurredRecord
        .setBpmnElementType(BpmnElementType.START_EVENT)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setElementId(elementId);

    // TODO (saig0): create the process instance by writing an ACTIVATE command (#6184)
    stateWriter.appendFollowUpEvent(
        eventOccurredKey, ProcessInstanceIntent.EVENT_OCCURRED, eventOccurredRecord);
  }

  private boolean isEventSubprocess(final ExecutableFlowElement catchEvent) {
    return catchEvent instanceof ExecutableStartEvent
        && ((ExecutableStartEvent) catchEvent).getEventSubProcess() != null;
  }
}
