/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.event;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;

public class IntermediateCatchEventProcessor
    implements BpmnElementProcessor<ExecutableCatchEventElement> {

  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;

  public IntermediateCatchEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
  }

  @Override
  public Class<ExecutableCatchEventElement> getType() {
    return ExecutableCatchEventElement.class;
  }

  @Override
  public void onActivating(
      final ExecutableCatchEventElement element, final BpmnElementContext context) {

    if (element.isConnectedToEventBasedGateway()) {
      // the event is already triggered on the event-based gateway when the activating record is
      // written for the intermediate catch event
      stateTransitionBehavior.transitionToActivated(context);
      return;
    }

    variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, context))
        .ifRightOrLeft(
            ok -> stateTransitionBehavior.transitionToActivated(context),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onActivated(
      final ExecutableCatchEventElement element, final BpmnElementContext context) {

    if (element.isConnectedToEventBasedGateway()) {
      stateTransitionBehavior.transitionToCompleting(context);
    }
  }

  @Override
  public void onCompleting(
      final ExecutableCatchEventElement element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .ifRightOrLeft(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(context);
              stateTransitionBehavior.transitionToCompleted(context);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onCompleted(
      final ExecutableCatchEventElement element, final BpmnElementContext context) {
    stateTransitionBehavior.takeOutgoingSequenceFlows(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableCatchEventElement element, final BpmnElementContext context) {
    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    stateTransitionBehavior.transitionToTerminated(context);
  }

  @Override
  public void onTerminated(
      final ExecutableCatchEventElement element, final BpmnElementContext context) {
    incidentBehavior.resolveIncidents(context);

    stateTransitionBehavior.onElementTerminated(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableCatchEventElement element, final BpmnElementContext context) {
    eventSubscriptionBehavior.triggerIntermediateEvent(context);
  }
}
