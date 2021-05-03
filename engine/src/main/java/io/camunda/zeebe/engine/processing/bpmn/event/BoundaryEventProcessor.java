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
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;

public final class BoundaryEventProcessor implements BpmnElementProcessor<ExecutableBoundaryEvent> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public BoundaryEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableBoundaryEvent> getType() {
    return ExecutableBoundaryEvent.class;
  }

  @Override
  public void onActivate(final ExecutableBoundaryEvent element, final BpmnElementContext context) {
    // the boundary event is activated by writing an ACTIVATING and ACTIVATED event to pass the
    // variables from the event for the output mapping
    throw new BpmnProcessingException(
        context,
        "Expected an ACTIVATING and ACTIVATED event for the boundary event but found an ACTIVATE command.");
  }

  @Override
  public void onComplete(final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    variableMappingBehavior
        .applyOutputMappings(context, element)
        .ifRightOrLeft(
            ok -> {
              final var completed =
                  stateTransitionBehavior.transitionToCompletedWithParentNotification(
                      element, context);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onTerminate(final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    incidentBehavior.resolveIncidents(context);

    final var terminated = stateTransitionBehavior.transitionToTerminated(context);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }
}
