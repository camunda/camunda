/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class EventHandle {

  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final ProcessInstanceRecord recordForPICreation = new ProcessInstanceRecord();
  private final MessageStartEventSubscriptionRecord startEventSubscriptionRecord =
      new MessageStartEventSubscriptionRecord();

  private final KeyGenerator keyGenerator;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final ProcessState processState;

  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final EventTriggerBehavior eventTriggerBehavior;
  private final BpmnStateBehavior stateBehavior;

  public EventHandle(
      final KeyGenerator keyGenerator,
      final EventScopeInstanceState eventScopeInstanceState,
      final Writers writers,
      final ProcessState processState,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior) {
    this.keyGenerator = keyGenerator;
    this.eventScopeInstanceState = eventScopeInstanceState;
    this.processState = processState;
    commandWriter = writers.command();
    stateWriter = writers.state();
    this.eventTriggerBehavior = eventTriggerBehavior;
    this.stateBehavior = stateBehavior;
  }

  public boolean canTriggerElement(
      final ElementInstance eventScopeInstance, final DirectBuffer elementId) {
    if (eventScopeInstance == null) {
      return false;
    }

    final ElementInstance flowScopeInstance =
        stateBehavior.getElementInstance(eventScopeInstance.getParentKey());

    return eventScopeInstance.isActive()
        && eventScopeInstanceState.canTriggerEvent(eventScopeInstance.getKey(), elementId)
        && (flowScopeInstance == null || !flowScopeInstance.isInterrupted());
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
  private long triggeringProcessEvent(
      final long processDefinitionKey,
      final long processInstanceKey,
      final String tenantId,
      final long eventScopeKey,
      final DirectBuffer catchEventId,
      final DirectBuffer variables) {
    return eventTriggerBehavior.triggeringProcessEvent(
        processDefinitionKey, processInstanceKey, tenantId, eventScopeKey, catchEventId, variables);
  }

  public void activateElement(
      final ExecutableFlowElement catchEvent,
      final long eventScopeKey,
      final ProcessInstanceRecord elementRecord) {
    activateElement(catchEvent, eventScopeKey, elementRecord, NO_VARIABLES);
  }

  public void activateElement(
      final ExecutableFlowElement catchEvent,
      final long eventScopeKey,
      final ProcessInstanceRecord elementRecord,
      final DirectBuffer variables) {

    final var processEventKey =
        triggeringProcessEvent(
            elementRecord.getProcessDefinitionKey(),
            elementRecord.getProcessInstanceKey(),
            elementRecord.getTenantId(),
            eventScopeKey,
            catchEvent.getId(),
            variables);

    if (isElementActivated(catchEvent)) {
      commandWriter.appendFollowUpCommand(
          eventScopeKey, ProcessInstanceIntent.COMPLETE_ELEMENT, elementRecord);
    } else if (catchEvent.getFlowScope().getElementType() == BpmnElementType.EVENT_SUB_PROCESS
        && catchEvent.getElementType() == BpmnElementType.START_EVENT) {
      final var startEvent = (ExecutableStartEvent) catchEvent;
      eventTriggerBehavior.triggerEventSubProcess(
          startEvent, eventScopeKey, elementRecord, variables);

    } else if (isInterrupting(catchEvent)) {
      // terminate the activated element and then activate the triggered catch event
      commandWriter.appendFollowUpCommand(
          eventScopeKey, ProcessInstanceIntent.TERMINATE_ELEMENT, elementRecord);
    } else {
      eventTriggerBehavior.activateTriggeredEvent(
          processEventKey,
          catchEvent,
          eventScopeKey,
          elementRecord.getFlowScopeKey(),
          elementRecord,
          variables);
    }
  }

  public void triggeringProcessEvent(final JobRecord jobRecord) {
    triggeringProcessEvent(
        jobRecord.getProcessDefinitionKey(),
        jobRecord.getProcessInstanceKey(),
        jobRecord.getTenantId(),
        jobRecord.getElementInstanceKey(),
        jobRecord.getElementIdBuffer(),
        jobRecord.getVariablesBuffer());
  }

  public void triggeringProcessEvent(final UserTaskRecord userTaskRecord) {
    triggeringProcessEvent(
        userTaskRecord.getProcessDefinitionKey(),
        userTaskRecord.getProcessInstanceKey(),
        userTaskRecord.getTenantId(),
        userTaskRecord.getElementInstanceKey(),
        userTaskRecord.getElementIdBuffer(),
        userTaskRecord.getVariablesBuffer());
  }

  private boolean isElementActivated(final ExecutableFlowElement catchEvent) {
    return catchEvent.getElementType() == BpmnElementType.INTERMEDIATE_CATCH_EVENT
        || catchEvent.getElementType() == BpmnElementType.RECEIVE_TASK
        || catchEvent.getElementType() == BpmnElementType.EVENT_BASED_GATEWAY;
  }

  private boolean isInterrupting(final ExecutableFlowElement catchEvent) {
    if (catchEvent instanceof ExecutableCatchEvent) {
      final var event = (ExecutableCatchEvent) catchEvent;
      return event.isInterrupting();
    } else {
      return false;
    }
  }

  public long triggerMessageStartEvent(
      final long subscriptionKey,
      final MessageStartEventSubscriptionRecord subscription,
      final long messageKey,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final DirectBuffer variables) {

    final var newProcessInstanceKey = keyGenerator.nextKey();
    startEventSubscriptionRecord
        .setProcessDefinitionKey(subscription.getProcessDefinitionKey())
        .setBpmnProcessId(subscription.getBpmnProcessIdBuffer())
        .setStartEventId(subscription.getStartEventIdBuffer())
        .setProcessInstanceKey(newProcessInstanceKey)
        .setCorrelationKey(correlationKey)
        .setMessageKey(messageKey)
        .setMessageName(messageName)
        .setVariables(variables)
        .setTenantId(subscription.getTenantId());

    stateWriter.appendFollowUpEvent(
        subscriptionKey,
        MessageStartEventSubscriptionIntent.CORRELATED,
        startEventSubscriptionRecord);

    activateProcessInstanceForStartEvent(
        subscription.getProcessDefinitionKey(),
        newProcessInstanceKey,
        startEventSubscriptionRecord.getStartEventIdBuffer(),
        variables,
        subscription.getTenantId());

    return newProcessInstanceKey;
  }

  public void activateProcessInstanceForStartEvent(
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer targetElementId,
      final DirectBuffer variablesBuffer,
      final String tenantId) {

    triggeringProcessEvent(
        processDefinitionKey,
        processInstanceKey,
        tenantId,
        processDefinitionKey /* The eventScope for the start event is the process definition key */,
        targetElementId,
        variablesBuffer);

    final var process = processState.getProcessByKeyAndTenant(processDefinitionKey, tenantId);

    recordForPICreation
        .setBpmnProcessId(process.getBpmnProcessId())
        .setProcessDefinitionKey(process.getKey())
        .setVersion(process.getVersion())
        .setProcessInstanceKey(processInstanceKey)
        .setRootProcessInstanceKey(processInstanceKey)
        .setElementId(process.getProcess().getId())
        .setBpmnElementType(process.getProcess().getElementType())
        .setTenantId(tenantId);

    commandWriter.appendFollowUpCommand(
        processInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, recordForPICreation);
  }
}
