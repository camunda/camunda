/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.CompensationSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;
import org.agrona.DirectBuffer;

public class BpmnCompensationSubscriptionBehaviour {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final CompensationSubscriptionState compensationSubscriptionState;
  private final ProcessState processState;
  private final TypedCommandWriter commandWriter;

  public BpmnCompensationSubscriptionBehaviour(
      final MutableProcessingState processingState, final Writers writers) {
    keyGenerator = processingState.getKeyGenerator();
    stateWriter = writers.state();
    compensationSubscriptionState = processingState.getCompensationSubscriptionState();
    processState = processingState.getProcessState();
    commandWriter = writers.command();
  }

  public Set<String> getCompletedActivitiesToCompensate(final BpmnElementContext context) {
    return compensationSubscriptionState.findSubscriptionsByProcessInstanceKey(
        context.getTenantId(), context.getProcessInstanceKey());
  }

  public void activateCompensationHandler(
      final String elementId, final BpmnElementContext context) {

    // find the boundary event
    final var activityToCompensate =
        processState.getFlowElement(
            context.getProcessDefinitionKey(),
            context.getTenantId(),
            BufferUtil.wrapString(elementId),
            ExecutableActivity.class);

    final ExecutableBoundaryEvent boundaryEvent =
        activityToCompensate.getBoundaryEvents().stream()
            .filter(b -> b.getEventType() == BpmnEventType.COMPENSATION)
            .findFirst()
            .orElseThrow();

    activateAndCompleteCompensationBoundaryEvent(context, boundaryEvent);

    // activate the compensation handler
    final ExecutableActivity compensationHandler =
        boundaryEvent.getCompensation().getCompensationHandler();
    final DirectBuffer compensationHandlerId = compensationHandler.getId();

    final ProcessInstanceRecord compensationHandlerRecord = new ProcessInstanceRecord();
    compensationHandlerRecord.wrap(context.getRecordValue());
    compensationHandlerRecord.setBpmnElementType(compensationHandler.getElementType());
    compensationHandlerRecord.setElementId(compensationHandlerId);

    commandWriter.appendNewCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, compensationHandlerRecord);
  }

  public void createCompensationSubscription(
      final ExecutableActivity element, final BpmnElementContext context) {
    if (hasCompensationBoundaryEvent(element)) {
      final var key = keyGenerator.nextKey();
      final var compensation =
          new CompensationSubscriptionRecord()
              .setTenantId(context.getTenantId())
              .setProcessInstanceKey(context.getProcessInstanceKey())
              .setProcessDefinitionKey(context.getProcessDefinitionKey())
              .setCompensableActivityId(BufferUtil.bufferAsString(context.getElementId()))
              .setCompensableActivityScopeId(
                  BufferUtil.bufferAsString(element.getFlowScope().getId()));
      stateWriter.appendFollowUpEvent(key, CompensationSubscriptionIntent.CREATED, compensation);
    }
  }

  public void triggerCompensationSubscription(final BpmnElementContext context) {
    final var key = keyGenerator.nextKey();
    final var compensation =
        new CompensationSubscriptionRecord()
            .setTenantId(context.getTenantId())
            .setProcessInstanceKey(context.getProcessInstanceKey())
            .setProcessDefinitionKey(context.getProcessDefinitionKey())
            .setThrowEventId(BufferUtil.bufferAsString(context.getElementId()))
            .setThrowEventInstanceKey(context.getElementInstanceKey());
    stateWriter.appendFollowUpEvent(key, CompensationSubscriptionIntent.TRIGGERED, compensation);
  }

  private boolean hasCompensationBoundaryEvent(final ExecutableActivity element) {
    final var compensationBoundaryEvent =
        element.getBoundaryEvents().stream()
            .filter(boundaryEvent -> boundaryEvent.getEventType() == BpmnEventType.COMPENSATION)
            .findFirst();
    return compensationBoundaryEvent.isPresent();
  }

  private void activateAndCompleteCompensationBoundaryEvent(
      final BpmnElementContext context, final ExecutableBoundaryEvent boundaryEvent) {
    final long boundaryEventKey = keyGenerator.nextKey();

    final ProcessInstanceRecord boundaryEventRecord = new ProcessInstanceRecord();
    boundaryEventRecord.wrap(context.getRecordValue());
    boundaryEventRecord.setBpmnElementType(BpmnElementType.BOUNDARY_EVENT);
    boundaryEventRecord.setElementId(boundaryEvent.getId());
    boundaryEventRecord.setBpmnEventType(BpmnEventType.COMPENSATION);

    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_COMPLETING, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_COMPLETED, boundaryEventRecord);
  }
}
