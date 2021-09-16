/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;

public class ManualTaskProcessor implements BpmnElementProcessor<ExecutableActivity> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public ManualTaskProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
  }

  @Override
  public Class<ExecutableActivity> getType() {
    return ExecutableActivity.class;
  }

  @Override
  public void onActivate(final ExecutableActivity element, final BpmnElementContext context) {
    final var activated = stateTransitionBehavior.transitionToActivated(context);
    stateTransitionBehavior.completeElement(activated);
  }

  @Override
  public void onComplete(final ExecutableActivity element, final BpmnElementContext context) {
    stateTransitionBehavior
        .transitionToCompleted(element, context)
        .ifRightOrLeft(
            completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onTerminate(final ExecutableActivity element, final BpmnElementContext context) {
    final var terminated = stateTransitionBehavior.transitionToTerminated(context);
    incidentBehavior.resolveIncidents(context);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }
}
