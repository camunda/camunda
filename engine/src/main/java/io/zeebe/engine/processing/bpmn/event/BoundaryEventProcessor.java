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
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;

public final class BoundaryEventProcessor implements BpmnElementProcessor<ExecutableBoundaryEvent> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public BoundaryEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableBoundaryEvent> getType() {
    return ExecutableBoundaryEvent.class;
  }

  @Override
  public void onActivating(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {
    // the boundary event is already triggered when the activating event is written

    stateTransitionBehavior.transitionToActivated(context);
  }

  @Override
  public void onActivated(final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    stateTransitionBehavior.transitionToCompleting(context);
  }

  @Override
  public void onCompleting(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    variableMappingBehavior
        .applyOutputMappings(context, element)
        .ifRightOrLeft(
            ok -> stateTransitionBehavior.transitionToCompleted(context),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onCompleted(final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    stateTransitionBehavior.takeOutgoingSequenceFlows(element, context);

    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    stateTransitionBehavior.transitionToTerminated(context);
  }

  @Override
  public void onTerminated(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    incidentBehavior.resolveIncidents(context);

    stateTransitionBehavior.onElementTerminated(element, context);

    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    throw new BpmnProcessingException(
        context,
        "Expected to handle occurred event on a boundary event, but events should not occur on a boundary event.");
  }
}
