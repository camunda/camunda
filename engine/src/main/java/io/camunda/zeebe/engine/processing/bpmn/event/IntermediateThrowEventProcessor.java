/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.event;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;

public class IntermediateThrowEventProcessor implements BpmnElementProcessor<ExecutableFlowNode> {

  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public IntermediateThrowEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableFlowNode> getType() {
    return ExecutableFlowNode.class;
  }

  @Override
  public void onActivate(final ExecutableFlowNode element, final BpmnElementContext context) {
    final var activated = stateTransitionBehavior.transitionToActivated(context);
    stateTransitionBehavior.completeElement(activated);
  }

  @Override
  public void onComplete(final ExecutableFlowNode element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, context))
        .ifRightOrLeft(
            completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onTerminate(final ExecutableFlowNode element, final BpmnElementContext context) {
    final var terminated = stateTransitionBehavior.transitionToTerminated(context);
    incidentBehavior.resolveIncidents(terminated);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }
}
