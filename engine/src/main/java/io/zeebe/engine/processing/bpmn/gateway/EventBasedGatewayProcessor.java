/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.gateway;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableEventBasedGateway;

public final class EventBasedGatewayProcessor
    implements BpmnElementProcessor<ExecutableEventBasedGateway> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public EventBasedGatewayProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableEventBasedGateway> getType() {
    return ExecutableEventBasedGateway.class;
  }

  @Override
  public void onActivating(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    eventSubscriptionBehavior
        .subscribeToEvents(element, context)
        .ifRightOrLeft(
            ok -> stateTransitionBehavior.transitionToActivated(context),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onActivated(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    // nothing to do - await that an event occurs
  }

  @Override
  public void onCompleting(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    stateTransitionBehavior.transitionToCompleted(context);
  }

  @Override
  public void onCompleted(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    // continue on the event of the gateway that was triggered
    // according to the BPMN specification, the sequence flow to this event is not taken
    eventSubscriptionBehavior.publishTriggeredEventBasedGateway(context);

    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    stateTransitionBehavior.transitionToTerminated(context);
  }

  @Override
  public void onTerminated(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    incidentBehavior.resolveIncidents(context);

    stateTransitionBehavior.onElementTerminated(element, context);

    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableEventBasedGateway element, final BpmnElementContext context) {

    eventSubscriptionBehavior.triggerEventBasedGateway(element, context);
  }
}
