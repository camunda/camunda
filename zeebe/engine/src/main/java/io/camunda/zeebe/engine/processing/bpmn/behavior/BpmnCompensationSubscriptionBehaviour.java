/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static java.util.function.Predicate.not;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCompensation;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.compensation.CompensationSubscription;
import io.camunda.zeebe.engine.state.immutable.CompensationSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class BpmnCompensationSubscriptionBehaviour {

  /** Default instance key if no compensation handler was activated. */
  private static final long NONE_COMPENSATION_HANDLER_INSTANCE_KEY = -1L;

  private static final Predicate<CompensationSubscription> TRIGGER_ALL_SUBSCRIPTIONS =
      subscription -> true;

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final CompensationSubscriptionState compensationSubscriptionState;
  private final ProcessState processState;
  private final TypedCommandWriter commandWriter;
  private final BpmnStateBehavior stateBehavior;

  public BpmnCompensationSubscriptionBehaviour(
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final Writers writers,
      final BpmnStateBehavior stateBehavior) {
    this.keyGenerator = keyGenerator;
    processState = processingState.getProcessState();
    compensationSubscriptionState = processingState.getCompensationSubscriptionState();
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.stateBehavior = stateBehavior;
  }

  public void createCompensationSubscription(
      final ExecutableActivity element, final BpmnElementContext context) {

    if (hasCompensationBoundaryEvent(element) || isFlowScopeWithSubscriptions(context)) {

      final var key = keyGenerator.nextKey();
      final var elementId = BufferUtil.bufferAsString(element.getId());

      final var compensation =
          new CompensationSubscriptionRecord()
              .setTenantId(context.getTenantId())
              .setProcessInstanceKey(context.getProcessInstanceKey())
              .setProcessDefinitionKey(context.getProcessDefinitionKey())
              .setCompensableActivityId(elementId)
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

  private static boolean hasCompensationSubscriptionInScope(
      final Collection<CompensationSubscription> subscriptions, final long scopeKey) {
    return subscriptions.stream()
        .anyMatch(
            subscription -> subscription.getRecord().getCompensableActivityScopeKey() == scopeKey);
  }

  public Optional<String> getCompensationHandlerId(final ExecutableActivity element) {
    return element.getBoundaryEvents().stream()
        .map(ExecutableBoundaryEvent::getCompensation)
        .filter(Objects::nonNull)
        .map(ExecutableCompensation::getCompensationHandler)
        .map(ExecutableActivity::getId)
        .map(BufferUtil::bufferAsString)
        .findFirst();
  }

  public boolean triggerCompensation(
      final ExecutableFlowElement element, final BpmnElementContext context) {
    // trigger the compensation in the scope of the compensation throw event
    return triggerCompensationInScope(element, context, TRIGGER_ALL_SUBSCRIPTIONS);
  }

  public boolean triggerCompensationForActivity(
      final ExecutableFlowElement element,
      final ExecutableActivity compensationActivity,
      final BpmnElementContext context) {
    final String compensationActivityId = BufferUtil.bufferAsString(compensationActivity.getId());
    // trigger the compensation for the given activity
    return triggerCompensationInScope(
        element,
        context,
        subscription ->
            subscription.getRecord().getCompensableActivityId().equals(compensationActivityId));
  }

  private boolean triggerCompensationInScope(
      final ExecutableFlowElement element,
      final BpmnElementContext context,
      final Predicate<CompensationSubscription> subscriptionFilter) {
    final List<Long> compensationScopeKeys = getCompensationScopeKeys(element, context);

    // ignore subscriptions that are already triggered
    final var notTriggeredSubscriptions =
        compensationSubscriptionState
            .findSubscriptionsByProcessInstanceKey(
                context.getTenantId(), context.getProcessInstanceKey())
            .stream()
            .filter(not(BpmnCompensationSubscriptionBehaviour::isCompensationTriggered))
            .toList();

    // filter subscriptions by their scope
    final var subscriptionsWithinScope =
        notTriggeredSubscriptions.stream()
            .filter(
                subscription ->
                    compensationScopeKeys.contains(
                        subscription.getRecord().getCompensableActivityScopeKey()))
            .filter(subscriptionFilter)
            .toList();

    if (subscriptionsWithinScope.isEmpty()) {
      return false; // no subscriptions are triggered
    }

    // trigger the compensation for all subscriptions in the scopes
    subscriptionsWithinScope.forEach(
        subscription ->
            triggerCompensationForSubscription(context, notTriggeredSubscriptions, subscription));
    return true;
  }

  private List<Long> getCompensationScopeKeys(
      final ExecutableFlowElement element, final BpmnElementContext context) {
    final long compensationEventScopeKey = context.getFlowScopeKey();

    if (isElementInsideEventSubprocess(element)) {
      // inside an event subprocess, trigger the compensation also from outside the event subprocess
      final BpmnElementContext flowScopeContext = stateBehavior.getFlowScopeContext(context);
      final long eventSubprocessScopeKey = flowScopeContext.getFlowScopeKey();

      return List.of(compensationEventScopeKey, eventSubprocessScopeKey);

    } else {
      return List.of(compensationEventScopeKey);
    }
  }

  private static boolean isElementInsideEventSubprocess(final ExecutableFlowElement element) {
    return element.getFlowScope().getElementType() == BpmnElementType.EVENT_SUB_PROCESS;
  }

  private static boolean isCompensationTriggered(
      final CompensationSubscription compensationSubscription) {
    return !compensationSubscription.getRecord().getThrowEventId().isEmpty();
  }

  private void triggerCompensationForSubscription(
      final BpmnElementContext context,
      final Collection<CompensationSubscription> subscriptions,
      final CompensationSubscription subscription) {
    long compensationHandlerInstanceKey = NONE_COMPENSATION_HANDLER_INSTANCE_KEY;

    // invoke the compensation handler if present
    if (hasCompensationHandler(subscription)) {
      compensationHandlerInstanceKey =
          activateCompensationHandler(context, subscription.getRecord().getCompensableActivityId());
    }

    // mark the subscription as triggered
    appendCompensationSubscriptionTriggerEvent(
        context, subscription, compensationHandlerInstanceKey);

    // propagate the compensation to subprocesses
    triggerCompensationFromTopToBottom(
        context, subscriptions, subscription.getRecord().getCompensableActivityInstanceKey());
  }

  private static boolean hasCompensationHandler(final CompensationSubscription subscription) {
    return !subscription.getRecord().getCompensationHandlerId().isEmpty();
  }

  private long activateCompensationHandler(
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

    final long compensationHandlerInstanceKey = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(
        compensationHandlerInstanceKey,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        compensationHandlerRecord);

    return compensationHandlerInstanceKey;
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

    final var elementTreePath =
        stateBehavior.getElementTreePath(
            boundaryEventKey, context.getFlowScopeKey(), boundaryEventRecord);
    boundaryEventRecord
        .setElementInstancePath(elementTreePath.elementInstancePath())
        .setProcessDefinitionPath(elementTreePath.processDefinitionPath())
        .setCallingElementPath(elementTreePath.callingElementPath());

    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_COMPLETING, boundaryEventRecord);
    stateWriter.appendFollowUpEvent(
        boundaryEventKey, ProcessInstanceIntent.ELEMENT_COMPLETED, boundaryEventRecord);
  }

  private void appendCompensationSubscriptionTriggerEvent(
      final BpmnElementContext context,
      final CompensationSubscription subscription,
      final long compensationHandlerInstanceKey) {

    final var key = subscription.getKey();
    final var compensationRecord = subscription.getRecord();
    compensationRecord
        .setThrowEventId(BufferUtil.bufferAsString(context.getElementId()))
        .setThrowEventInstanceKey(context.getElementInstanceKey())
        .setCompensationHandlerInstanceKey(compensationHandlerInstanceKey);

    stateWriter.appendFollowUpEvent(
        key, CompensationSubscriptionIntent.TRIGGERED, compensationRecord);
  }

  private void triggerCompensationFromTopToBottom(
      final BpmnElementContext context,
      final Collection<CompensationSubscription> subscriptions,
      final long scopeKey) {

    subscriptions.stream()
        .filter(
            subscription -> scopeKey == subscription.getRecord().getCompensableActivityScopeKey())
        .forEach(
            subscription ->
                triggerCompensationForSubscription(context, subscriptions, subscription));
  }

  public void completeCompensationHandler(final BpmnElementContext context) {

    if (BpmnEventType.COMPENSATION != context.getBpmnEventType()) {
      return; // avoid accessing the state for non-compensation handlers
    }

    // complete the subscription of the compensation handler
    compensationSubscriptionState
        .findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey())
        .stream()
        .filter(BpmnCompensationSubscriptionBehaviour::isCompensationTriggered)
        .filter(
            subscription ->
                subscription.getRecord().getCompensationHandlerInstanceKey()
                    == context.getElementInstanceKey())
        .findFirst()
        .ifPresent(compensation -> completeSubscription(context, compensation));
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
        .filter(not(BpmnCompensationSubscriptionBehaviour::hasCompensationHandler))
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

    Optional.ofNullable(stateBehavior.getElementInstance(throwEventInstanceKey))
        .ifPresent(
            compensationThrowEvent -> {
              final long elementInstanceKey = compensationThrowEvent.getKey();
              final var elementRecord = compensationThrowEvent.getValue();

              commandWriter.appendFollowUpCommand(
                  elementInstanceKey, ProcessInstanceIntent.COMPLETE_ELEMENT, elementRecord);
            });
  }

  public void deleteSubscriptionsOfProcessInstanceFilter(
      final BpmnElementContext context,
      final Predicate<CompensationSubscription> compensationSubscriptionFilter) {
    // delete all compensation subscriptions of the process instance matching the filter
    compensationSubscriptionState
        .findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey())
        .stream()
        .filter(compensationSubscriptionFilter)
        .forEach(this::appendCompensationSubscriptionDeleteEvent);
  }

  public void deleteSubscriptionsOfProcessInstance(final BpmnElementContext context) {
    // delete all compensation subscriptions of the process instance
    compensationSubscriptionState
        .findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey())
        .forEach(this::appendCompensationSubscriptionDeleteEvent);
  }

  public void deleteSubscriptionsOfSubprocess(final BpmnElementContext context) {
    final var subscriptions =
        compensationSubscriptionState.findSubscriptionsByProcessInstanceKey(
            context.getTenantId(), context.getProcessInstanceKey());

    // remove subprocess subscriptions in this scope (recursively) from the state
    deleteSubscriptionsTopToBottom(subscriptions, context.getElementInstanceKey());
  }

  private void deleteSubscriptionsTopToBottom(
      final Collection<CompensationSubscription> subscriptions, final long scopeKey) {
    subscriptions.stream()
        .filter(
            subscription -> scopeKey == subscription.getRecord().getCompensableActivityScopeKey())
        .forEach(
            subscription -> {
              appendCompensationSubscriptionDeleteEvent(subscription);
              deleteSubscriptionsTopToBottom(
                  subscriptions, subscription.getRecord().getCompensableActivityInstanceKey());
            });
  }

  public List<CompensationSubscription> getSubscriptionsByProcessInstanceKey(
      final BpmnElementContext context) {
    return compensationSubscriptionState.findSubscriptionsByProcessInstanceKey(
        context.getTenantId(), context.getProcessInstanceKey());
  }

  private void appendCompensationSubscriptionDeleteEvent(
      final CompensationSubscription subscription) {
    stateWriter.appendFollowUpEvent(
        subscription.getKey(), CompensationSubscriptionIntent.DELETED, subscription.getRecord());
  }
}
