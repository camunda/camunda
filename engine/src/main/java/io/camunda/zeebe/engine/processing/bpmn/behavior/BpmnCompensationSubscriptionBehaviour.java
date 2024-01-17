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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCompensation;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.compensation.CompensationSubscription;
import io.camunda.zeebe.engine.state.immutable.CompensationSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class BpmnCompensationSubscriptionBehaviour {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final CompensationSubscriptionState compensationSubscriptionState;
  private final ProcessState processState;
  private final TypedCommandWriter commandWriter;
  private final ElementInstanceState elementInstanceState;

  public BpmnCompensationSubscriptionBehaviour(
      final MutableProcessingState processingState, final Writers writers) {
    keyGenerator = processingState.getKeyGenerator();
    stateWriter = writers.state();
    compensationSubscriptionState = processingState.getCompensationSubscriptionState();
    processState = processingState.getProcessState();
    commandWriter = writers.command();
    elementInstanceState = processingState.getElementInstanceState();
  }

  public void createCompensationSubscription(
      final ExecutableActivity element, final BpmnElementContext context) {
    if (hasCompensationBoundaryEvent(element)) {
      final var key = keyGenerator.nextKey();
      final var compensationHandlerId = getCompensationHandlerId(element);
      final var compensation =
          new CompensationSubscriptionRecord()
              .setTenantId(context.getTenantId())
              .setProcessInstanceKey(context.getProcessInstanceKey())
              .setProcessDefinitionKey(context.getProcessDefinitionKey())
              .setCompensableActivityId(BufferUtil.bufferAsString(context.getElementId()))
              .setCompensableActivityScopeId(
                  BufferUtil.bufferAsString(element.getFlowScope().getId()))
              .setCompensationHandlerId(compensationHandlerId);
      stateWriter.appendFollowUpEvent(key, CompensationSubscriptionIntent.CREATED, compensation);
    }
  }

  public boolean triggerCompensation(
      final ExecutableFlowNode element, final BpmnElementContext context) {
    final Set<CompensationSubscription> completedCompensationSubscription =
        getCompensationSubscriptionForCompletedActivities(context);

    if (completedCompensationSubscription.isEmpty()) {
      return false;
    }

    // trigger compensation on process level
    triggerCompensationForScopeId(
        context, BufferUtil.bufferAsString(element.getFlowScope().getId()));

    final var completedSubprocessScopeId =
        getCompletedSubprocessScopeId(context.getTenantId(), context.getProcessInstanceKey());
    final var availableScopeId = getAvailableSubprocessScopeId(element);

    availableScopeId.forEach(
        scopeId -> {
          if (completedSubprocessScopeId.contains(scopeId)) {
            triggerCompensationForScopeId(context, scopeId);
          }
        });
    return true;
  }

  public void completeCompensationHandler(
      final BpmnElementContext context, final ExecutableActivity element) {
    if (BpmnEventType.COMPENSATION.equals(context.getBpmnEventType())) {
      final var tenantId = context.getTenantId();
      final var processInstanceKey = context.getProcessInstanceKey();
      compensationSubscriptionState
          .findSubscriptionByCompensationHandlerId(
              tenantId, processInstanceKey, BufferUtil.bufferAsString(element.getId()))
          .ifPresent(
              compensation -> {
                stateWriter.appendFollowUpEvent(
                    compensation.getKey(),
                    CompensationSubscriptionIntent.COMPLETED,
                    compensation.getRecord());

                final var compensations =
                    compensationSubscriptionState.findSubscriptionsByThrowEventInstanceKey(
                        tenantId,
                        processInstanceKey,
                        compensation.getRecord().getThrowEventInstanceKey());
                if (compensations.isEmpty()) {
                  completeCompensationThrowEvent(
                      compensation.getRecord().getThrowEventInstanceKey());
                }
              });
    }
  }

  public void deleteNotTriggeredSubscriptions(final BpmnElementContext context) {
    final var notTriggeredSubscriptions =
        compensationSubscriptionState.findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey());
    notTriggeredSubscriptions.forEach(
        compensation ->
            stateWriter.appendFollowUpEvent(
                compensation.getKey(),
                CompensationSubscriptionIntent.DELETED,
                compensation.getRecord()));
  }

  public void createCompensationSubscriptionForSubprocess(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    if (hasCompletedActivityWithCompensationHandler(element, context)) {
      final var key = keyGenerator.nextKey();
      final var compensation =
          new CompensationSubscriptionRecord()
              .setTenantId(context.getTenantId())
              .setProcessInstanceKey(context.getProcessInstanceKey())
              .setProcessDefinitionKey(context.getProcessDefinitionKey())
              .setCompensableActivityScopeId(BufferUtil.bufferAsString(element.getId()))
              .setSubprocessSubscription(true);
      stateWriter.appendFollowUpEvent(key, CompensationSubscriptionIntent.CREATED, compensation);
    }
  }

  public void deleteSubprocessSubscriptions(final BpmnElementContext context) {
    final var subprocessSubscriptions =
        compensationSubscriptionState.findSubprocessSubscriptions(
            context.getTenantId(), context.getProcessInstanceKey());
    subprocessSubscriptions.forEach(
        compensation ->
            stateWriter.appendFollowUpEvent(
                compensation.getKey(),
                CompensationSubscriptionIntent.DELETED,
                compensation.getRecord()));
  }

  private Set<CompensationSubscription> getCompensationSubscriptionForCompletedActivities(
      final BpmnElementContext context) {
    return compensationSubscriptionState.findSubscriptionsByProcessInstanceKey(
        context.getTenantId(), context.getProcessInstanceKey());
  }

  private boolean hasCompensationBoundaryEvent(final ExecutableActivity element) {
    final var compensationBoundaryEvent =
        element.getBoundaryEvents().stream()
            .filter(boundaryEvent -> boundaryEvent.getEventType() == BpmnEventType.COMPENSATION)
            .findFirst();
    return compensationBoundaryEvent.isPresent();
  }

  private void activateCompensationHandler(
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

  private String getCompensationHandlerId(final ExecutableActivity element) {
    final var compensationHandlerId =
        element.getBoundaryEvents().stream()
            .map(ExecutableBoundaryEvent::getCompensation)
            .filter(Objects::nonNull)
            .map(ExecutableCompensation::getCompensationHandler)
            .map(ExecutableActivity::getId)
            .findFirst();
    return compensationHandlerId.map(BufferUtil::bufferAsString).orElse(null);
  }

  private void completeCompensationThrowEvent(final long throwEventInstanceKey) {

    Optional.ofNullable(elementInstanceState.getInstance(throwEventInstanceKey))
        .ifPresent(
            compensationThrowEvent -> {
              final long compensationThrowElementInstanceKey = compensationThrowEvent.getKey();
              final ProcessInstanceRecord compensationRecord = compensationThrowEvent.getValue();

              commandWriter.appendFollowUpCommand(
                  compensationThrowElementInstanceKey,
                  ProcessInstanceIntent.COMPLETE_ELEMENT,
                  compensationRecord);
            });
  }

  private boolean hasCompletedActivityWithCompensationHandler(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    return !compensationSubscriptionState
        .findSubscriptionsByCompensableActivityScopeId(
            context.getTenantId(),
            context.getProcessInstanceKey(),
            BufferUtil.bufferAsString(element.getId()))
        .isEmpty();
  }

  private void triggerCompensationForScopeId(
      final BpmnElementContext context, final String scopeId) {
    final Set<CompensationSubscription> subscriptions =
        compensationSubscriptionState.findSubscriptionsByCompensableActivityScopeId(
            context.getTenantId(), context.getProcessInstanceKey(), scopeId);

    if (subscriptions.isEmpty()) {
      return;
    }
    // activate the compensation handler
    subscriptions.forEach(
        compensation -> {
          final var key = compensation.getKey();
          final var compensationRecord = compensation.getRecord();
          compensationRecord
              .setThrowEventId(BufferUtil.bufferAsString(context.getElementId()))
              .setThrowEventInstanceKey(context.getElementInstanceKey());
          stateWriter.appendFollowUpEvent(
              key, CompensationSubscriptionIntent.TRIGGERED, compensationRecord);

          activateCompensationHandler(compensationRecord.getCompensableActivityId(), context);
        });
  }

  private Set<String> getCompletedSubprocessScopeId(
      final String tenantId, final long processInstanceKey) {
    return compensationSubscriptionState
        .findSubprocessSubscriptions(tenantId, processInstanceKey)
        .stream()
        .map(
            compensationSubscription ->
                compensationSubscription.getRecord().getCompensableActivityScopeId())
        .collect(Collectors.toSet());
  }

  private Set<String> getAvailableSubprocessScopeId(final ExecutableFlowNode element) {
    return ((ExecutableProcess) element.getFlowScope())
        .getFlowElements().stream()
            .filter(e -> BpmnElementType.SUB_PROCESS.equals(e.getElementType()))
            .map(e -> BufferUtil.bufferAsString(e.getId()))
            .collect(Collectors.toSet());
  }
}
