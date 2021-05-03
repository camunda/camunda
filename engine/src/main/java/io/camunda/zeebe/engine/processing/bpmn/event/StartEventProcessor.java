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
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;

public class StartEventProcessor implements BpmnElementProcessor<ExecutableStartEvent> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public StartEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
  }

  @Override
  public Class<ExecutableStartEvent> getType() {
    return ExecutableStartEvent.class;
  }

  @Override
  public void onActivate(final ExecutableStartEvent element, final BpmnElementContext context) {
    final var activated = stateTransitionBehavior.transitionToActivated(context);
    stateTransitionBehavior.completeElement(activated);
  }

  @Override
  public void onComplete(final ExecutableStartEvent element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .map(
            c ->
                stateTransitionBehavior.transitionToCompletedWithParentNotification(
                    element, context))
        .ifRightOrLeft(
            completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onTerminate(final ExecutableStartEvent element, final BpmnElementContext context) {
    final var terminated = stateTransitionBehavior.transitionToTerminated(context);

    incidentBehavior.resolveIncidents(terminated);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }
}
