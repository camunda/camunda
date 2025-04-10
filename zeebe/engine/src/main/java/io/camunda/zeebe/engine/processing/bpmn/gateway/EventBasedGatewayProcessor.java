/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.gateway;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEventBasedGateway;
import io.camunda.zeebe.util.Either;

public final class EventBasedGatewayProcessor
    implements BpmnElementProcessor<ExecutableEventBasedGateway> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnJobBehavior jobBehavior;

  public EventBasedGatewayProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    this.stateTransitionBehavior = stateTransitionBehavior;
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableEventBasedGateway> getType() {
    return ExecutableEventBasedGateway.class;
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {
    return eventSubscriptionBehavior
        .subscribeToEvents(element, context)
        .thenDo(
            ok -> stateTransitionBehavior.transitionToActivated(context, element.getEventType()));
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {
    return SUCCESS.thenDo(ok -> eventSubscriptionBehavior.unsubscribeFromEvents(context));
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    final var eventTrigger =
        eventSubscriptionBehavior
            .findEventTrigger(context)
            .orElseThrow(
                () ->
                    new BpmnProcessingException(
                        context,
                        "Expected an event trigger to complete the event-based gateway but not found."));

    // transition to completed and continue on the event of the gateway that was triggered
    // - according to the BPMN specification, the sequence flow to this event is not taken
    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(
            completed ->
                eventSubscriptionBehavior.activateTriggeredEvent(
                    context.getElementInstanceKey(),
                    completed.getFlowScopeKey(),
                    eventTrigger,
                    completed));
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);

    final var terminated =
        stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
  }
}
