/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

public final class EventSubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public EventSubProcessProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableFlowElementContainer> getType() {
    return ExecutableFlowElementContainer.class;
  }

  @Override
  public void onActivate(
      final ExecutableFlowElementContainer element, final BpmnElementContext activating) {
    variableMappingBehavior
        .applyInputMappings(activating, element)
        .ifRightOrLeft(
            ok -> {
              final var activated = stateTransitionBehavior.transitionToActivated(activating);
              stateTransitionBehavior.activateChildInstance(
                  activated, element.getStartEvents().get(0));
            },
            failure -> {
              incidentBehavior.createIncident(failure, activating);
            });
  }

  @Override
  public void onComplete(
      final ExecutableFlowElementContainer element, final BpmnElementContext completing) {

    variableMappingBehavior
        .applyOutputMappings(completing, element)
        .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
        .ifLeft(failure -> incidentBehavior.createIncident(failure, completing));
  }

  @Override
  public void onTerminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext terminating) {

    incidentBehavior.resolveIncidents(terminating);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(terminating);
    if (noActiveChildInstances) {
      onChildTerminated(element, terminating, null);
    }
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    if (stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (childContext == null
        || (flowScopeContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING
            && stateBehavior.canBeTerminated(childContext))) {
      final var terminated = stateTransitionBehavior.transitionToTerminated(flowScopeContext);
      stateTransitionBehavior.onElementTerminated(element, terminated);
    }
  }
}
