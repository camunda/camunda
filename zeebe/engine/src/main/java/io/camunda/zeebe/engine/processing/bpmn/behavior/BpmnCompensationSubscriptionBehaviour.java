/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static java.util.function.Predicate.not;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCompensation;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
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
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    final var elementId = BufferUtil.bufferAsString(element.getId());

    if (hasCompensationBoundaryEvent(element) || isFlowScopeWithSubscriptions(context)) {

      final var key = keyGenerator.nextKey();
      final var flowScopeId = BufferUtil.bufferAsString(element.getFlowScope().getId());

      final var compensation =
          new CompensationSubscriptionRecord()
              .setTenantId(context.getTenantId())
              .setProcessInstanceKey(context.getProcessInstanceKey())
              .setProcessDefinitionKey(context.getProcessDefinitionKey())
              .setCompensableActivityId(elementId)
              .setCompensableActivityScopeId(flowScopeId)
              .setCompensableActivityInstanceKey(context.getElementInstanceKey())
              .setCompensableActivityScopeKey(context.getFlowScopeKey());

      getCompensationHandlerId(element).ifPresent(compensation::setCompensationHandlerId);

      stateWriter.appendFollowUpEvent(key, CompensationSubscriptionIntent.CREATED, compensation);
    }
  }

  private boolean hasCompensationBoundaryEvent(final ExecutableActivity element) {
    return element.getBoundaryEvents().stream()
        .anyMatch(boundaryEvent -> boundaryEvent.getEventType() == BpmnEventType.COMPENSATION);
  }

  private boolean isFlowScopeWithSubscriptions(final BpmnElementContext context) {
    final BpmnElementType bpmnElementType = context.getBpmnElementType();
    if (bpmnElementType != BpmnElementType.SUB_PROCESS
        && bpmnElementType != BpmnElementType.MULTI_INSTANCE_BODY) {
      return false; // avoid accessing the state for elements that are not a flow scope
    }

    final var subscriptions =
        compensationSubscriptionState.findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey());

    return hasCompensationSubscriptionInScope(subscriptions, context.getElementInstanceKey());
  }

  private Optional<String> getCompensationHandlerId(final ExecutableActivity element) {
    return element.getBoundaryEvents().stream()
        .map(ExecutableBoundaryEvent::getCompensation)
        .filter(Objects::nonNull)
        .map(ExecutableCompensation::getCompensationHandler)
        .map(ExecutableActivity::getId)
        .map(BufferUtil::bufferAsString)
        .findFirst();
  }

  public boolean triggerCompensation(final BpmnElementContext context) {
    // ignore subscriptions that are already triggered
    final var subscriptions =
        compensationSubscriptionState
            .findSubscriptionsByProcessInstanceKey(
                context.getTenantId(), context.getProcessInstanceKey())
            .stream()
            .filter(not(BpmnCompensationSubscriptionBehaviour::isCompensationTriggered))
            .collect(Collectors.toSet());

    if (hasCompensationSubscriptionInScope(subscriptions, context.getFlowScopeKey())) {
      // trigger the compensation in the current scope and propagate to the subprocesses
      triggerCompensationFromTopToBottom(context, subscriptions, context.getFlowScopeKey());
      return true;

    } else {
      return false;
    }
  }

  private static boolean hasCompensationSubscriptionInScope(
      final Set<CompensationSubscription> subscriptions, final long scopeKey) {
    return subscriptions.stream()
        .anyMatch(
            subscription -> subscription.getRecord().getCompensableActivityScopeKey() == scopeKey);
  }

  private static boolean isCompensationTriggered(
      final CompensationSubscription compensationSubscription) {
    return !compensationSubscription.getRecord().getThrowEventId().isEmpty();
  }

  private void triggerCompensationFromTopToBottom(
      final BpmnElementContext context,
      final Collection<CompensationSubscription> subscriptions,
      final long scopeKey) {

    subscriptions.stream()
        .filter(
            subscription -> scopeKey == subscription.getRecord().getCompensableActivityScopeKey())
        .forEach(
            subscription -> {
              // mark the subscription as triggered
              appendCompensationSubscriptionTriggerEvent(context, subscription);

              // invoke the compensation handler if present
              if (!subscription.getRecord().getCompensationHandlerId().isEmpty()) {
                activateCompensationHandler(
                    context, subscription.getRecord().getCompensableActivityId());
              }

              // propagate the compensation to subprocesses
              triggerCompensationFromTopToBottom(
                  context,
                  subscriptions,
                  subscription.getRecord().getCompensableActivityInstanceKey());
            });
  }

  private void appendCompensationSubscriptionTriggerEvent(
      final BpmnElementContext context, final CompensationSubscription subscription) {

    final var key = subscription.getKey();
    final var compensationRecord = subscription.getRecord();
    compensationRecord
        .setThrowEventId(BufferUtil.bufferAsString(context.getElementId()))
        .setThrowEventInstanceKey(context.getElementInstanceKey());

    stateWriter.appendFollowUpEvent(
        key, CompensationSubscriptionIntent.TRIGGERED, compensationRecord);
  }

  private void activateCompensationHandler(
      final BpmnElementContext context, final String elementId) {

    final var boundaryEvent = getCompensationBoundaryEvent(context, elementId);
    activateAndCompleteCompensationBoundaryEvent(context, boundaryEvent);

    // activate the compensation handler
    final var compensationHandler = boundaryEvent.getCompensation().getCompensationHandler();

    final ProcessInstanceRecord compensationHandlerRecord = new ProcessInstanceRecord();
    compensationHandlerRecord.wrap(context.getRecordValue());
    compensationHandlerRecord
        .setElementId(compensationHandler.getId())
        .setBpmnElementType(compensationHandler.getElementType())
        .setBpmnEventType(BpmnEventType.COMPENSATION);

    commandWriter.appendNewCommand(
        ProcessInstanceIntent.ACTIVATE_ELEMENT, compensationHandlerRecord);
  }

  private ExecutableBoundaryEvent getCompensationBoundaryEvent(
      final BpmnElementContext context, final String elementId) {

    var activityToCompensate =
        processState.getFlowElement(
            context.getProcessDefinitionKey(),
            context.getTenantId(),
            BufferUtil.wrapString(elementId),
            ExecutableActivity.class);

    if (activityToCompensate.getFlowScope()
        instanceof final ExecutableMultiInstanceBody multiInstanceBody) {
      activityToCompensate = multiInstanceBody;
    }

    return activityToCompensate.getBoundaryEvents().stream()
        .filter(b -> b.getEventType() == BpmnEventType.COMPENSATION)
        .findFirst()
        .orElseThrow(
            () ->
                new BpmnProcessingException(
                    context,
                    "No compensation boundary event found for activity '%s'".formatted(elementId)));
  }

  private void activateAndCompleteCompensationBoundaryEvent(
      final BpmnElementContext context, final ExecutableBoundaryEvent boundaryEvent) {

    final long boundaryEventKey = keyGenerator.nextKey();

    final ProcessInstanceRecord boundaryEventRecord = new ProcessInstanceRecord();
    boundaryEventRecord.wrap(context.getRecordValue());
    boundaryEventRecord
        .setElementId(boundaryEvent.getId())
        .setBpmnElementType(boundaryEvent.getElementType())
        .setBpmnEventType(boundaryEvent.getEventType());

    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_COMPLETING, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_COMPLETED, boundaryEventRecord);
  }

  public void completeCompensationHandler(
      final ExecutableActivity element, final BpmnElementContext context) {

    if (BpmnEventType.COMPENSATION != context.getBpmnEventType()) {
      return; // avoid accessing the state for non-compensation handlers
    }

    final var compensationHandlerId = BufferUtil.bufferAsString(element.getId());

    // complete the subscription of the compensation handler
    // - if there are multiple subscriptions for the same compensation handler then we may
    // complete the wrong subscription
    compensationSubscriptionState
        .findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey())
        .stream()
        .filter(BpmnCompensationSubscriptionBehaviour::isCompensationTriggered)
        .filter(
            subscription ->
                subscription.getRecord().getCompensationHandlerId().equals(compensationHandlerId))
        .forEach(compensation -> completeSubscription(context, compensation));
  }

  private void completeSubscription(
      final BpmnElementContext context, final CompensationSubscription compensation) {
    // mark the subscription as completed
    stateWriter.appendFollowUpEvent(
        compensation.getKey(), CompensationSubscriptionIntent.COMPLETED, compensation.getRecord());

    // complete the flow scope of the compensation handler if possible
    completeFlowScopeSubscriptionFromBottomToTop(
        context, compensation.getRecord().getCompensableActivityScopeKey());

    final long throwEventInstanceKey = compensation.getRecord().getThrowEventInstanceKey();
    if (!hasSubscriptionForThrowEvent(context, throwEventInstanceKey)) {
      // all compensation handlers are completed; we can complete the compensation throw event
      completeCompensationThrowEvent(throwEventInstanceKey);
    }
  }

  private void completeFlowScopeSubscriptionFromBottomToTop(
      final BpmnElementContext context, final long scopeKey) {

    final var subscriptions =
        compensationSubscriptionState.findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey());

    if (hasCompensationSubscriptionInScope(subscriptions, scopeKey)) {
      // we can't complete the flow scope until all compensations are completed
      return;
    }

    subscriptions.stream()
        .filter(
            subscription ->
                scopeKey == subscription.getRecord().getCompensableActivityInstanceKey())
        .findFirst()
        .ifPresent(
            flowScopeSubscription -> {
              // mark the flow scope subscription as completed
              stateWriter.appendFollowUpEvent(
                  flowScopeSubscription.getKey(),
                  CompensationSubscriptionIntent.COMPLETED,
                  flowScopeSubscription.getRecord());

              // complete the flow scope of the subscription if possible (recursively)
              completeFlowScopeSubscriptionFromBottomToTop(
                  context, flowScopeSubscription.getRecord().getCompensableActivityScopeKey());
            });
  }

  private boolean hasSubscriptionForThrowEvent(
      final BpmnElementContext context, final long throwEventInstanceKey) {
    return !compensationSubscriptionState
        .findSubscriptionsByThrowEventInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey(), throwEventInstanceKey)
        .isEmpty();
  }

  private void completeCompensationThrowEvent(final long throwEventInstanceKey) {

    Optional.ofNullable(elementInstanceState.getInstance(throwEventInstanceKey))
        .ifPresent(
            compensationThrowEvent -> {
              final long elementInstanceKey = compensationThrowEvent.getKey();
              final var elementRecord = compensationThrowEvent.getValue();

              commandWriter.appendFollowUpCommand(
                  elementInstanceKey, ProcessInstanceIntent.COMPLETE_ELEMENT, elementRecord);
            });
  }

  public void deleteSubscriptionsOfProcessInstance(final BpmnElementContext context) {
    // delete all compensation subscriptions of the process instance
    compensationSubscriptionState
        .findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey())
        .forEach(
            subscription ->
                stateWriter.appendFollowUpEvent(
                    subscription.getKey(),
                    CompensationSubscriptionIntent.DELETED,
                    subscription.getRecord()));
  }
}
