/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.task;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableReceiveTask;

public final class ReceiveTaskProcessor implements BpmnElementProcessor<ExecutableReceiveTask> {

  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;

  public ReceiveTaskProcessor(final BpmnBehaviors behaviors) {
    eventSubscriptionBehavior = behaviors.eventSubscriptionBehavior();
    incidentBehavior = behaviors.incidentBehavior();
    stateBehavior = behaviors.stateBehavior();
    stateTransitionBehavior = behaviors.stateTransitionBehavior();
    variableMappingBehavior = behaviors.variableMappingBehavior();
  }

  @Override
  public Class<ExecutableReceiveTask> getType() {
    return ExecutableReceiveTask.class;
  }

  @Override
  public void onActivating(final ExecutableReceiveTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, context))
        .ifRightOrLeft(
            ok -> stateTransitionBehavior.transitionToActivated(context),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onActivated(final ExecutableReceiveTask element, final BpmnElementContext context) {
    // nothing to do here
  }

  @Override
  public void onCompleting(final ExecutableReceiveTask element, final BpmnElementContext context) {
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
  public void onCompleted(final ExecutableReceiveTask element, final BpmnElementContext context) {
    stateTransitionBehavior.takeOutgoingSequenceFlows(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(final ExecutableReceiveTask element, final BpmnElementContext context) {
    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    stateTransitionBehavior.transitionToTerminated(context);
  }

  @Override
  public void onTerminated(final ExecutableReceiveTask element, final BpmnElementContext context) {
    eventSubscriptionBehavior.publishTriggeredBoundaryEvent(context);
    incidentBehavior.resolveIncidents(context);
    stateTransitionBehavior.onElementTerminated(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableReceiveTask element, final BpmnElementContext context) {
    eventSubscriptionBehavior.triggerBoundaryOrIntermediateEvent(element, context);
  }
}
