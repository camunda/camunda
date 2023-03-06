/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;

public final class BpmnEventSubscriptionBehavior {

  private final EventScopeInstanceState eventScopeInstanceState;
  private final CatchEventBehavior catchEventBehavior;

  private final ProcessState processState;
  private final EventTriggerBehavior eventTriggerBehavior;

  public BpmnEventSubscriptionBehavior(
      final CatchEventBehavior catchEventBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final ProcessingState processingState) {
    this.catchEventBehavior = catchEventBehavior;
    this.eventTriggerBehavior = eventTriggerBehavior;

    processState = processingState.getProcessState();
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
  }

  /**
   * @return either a failure or nothing
   */
  public <T extends ExecutableCatchEventSupplier> Either<Failure, Void> subscribeToEvents(
      final T element, final BpmnElementContext context) {
    return catchEventBehavior.subscribeToEvents(context, element);
  }

  public void unsubscribeFromEvents(final BpmnElementContext context) {
    catchEventBehavior.unsubscribeFromEvents(context.getElementInstanceKey());
  }

  /**
   * Checks if the given element instance was triggered by an event. Should be used in combination
   * with {@link #activateTriggeredEvent(long, long, EventTrigger, BpmnElementContext)}.
   *
   * @param context the element instance to check
   * @return the event data if the element instance was triggered. Otherwise, it returns {@link
   *     Optional#empty()}.
   */
  public Optional<EventTrigger> findEventTrigger(final BpmnElementContext context) {
    final var elementInstanceKey = context.getElementInstanceKey();
    // Event triggers could be used to store variables. If the event trigger belongs to the element
    // itself, we should not activate it, otherwise the element will be activated a second time.
    return Optional.ofNullable(eventScopeInstanceState.peekEventTrigger(elementInstanceKey))
        .filter(
            trigger -> !BufferUtil.contentsEqual(trigger.getElementId(), context.getElementId()));
  }

  /**
   * Activates the element that was triggered by an event and pass in the variables of the event.
   * Should be called after {@link #findEventTrigger(BpmnElementContext)}. Depending on the event
   * type we need to give different flow scope key, e. g. for EventSubProcess the container
   * triggers/activates the EventSubProcess, while for boundary events the attached element triggers
   * that event which is not the flow scope.
   *
   * @param eventScopeKey the event scope key of the triggered event, can be the same as the flow
   *     scope key depending of the type of event
   * @param flowScopeKey the flow scope of event element to activate, which can be different based
   *     on the event type
   * @param eventTrigger the event data returned by {@link #findEventTrigger(BpmnElementContext)}
   * @param context the current processing context
   */
  public void activateTriggeredEvent(
      final long eventScopeKey,
      final long flowScopeKey,
      final EventTrigger eventTrigger,
      final BpmnElementContext context) {

    final var triggeredEvent =
        processState.getFlowElement(
            context.getProcessDefinitionKey(),
            eventTrigger.getElementId(),
            ExecutableFlowElement.class);

    eventTriggerBehavior.activateTriggeredEvent(
        eventTrigger.getEventKey(),
        triggeredEvent,
        eventScopeKey,
        flowScopeKey,
        context.getRecordValue(),
        eventTrigger.getVariables());
  }

  public Optional<EventTrigger> getEventTriggerForProcessDefinition(
      final long processDefinitionKey) {
    final var eventTrigger = eventScopeInstanceState.peekEventTrigger(processDefinitionKey);
    return Optional.ofNullable(eventTrigger);
  }

  public void activateTriggeredStartEvent(
      final BpmnElementContext context, final EventTrigger eventTrigger) {

    activateTriggeredEvent(
        context.getProcessDefinitionKey(), context.getProcessInstanceKey(), eventTrigger, context);
  }
}
