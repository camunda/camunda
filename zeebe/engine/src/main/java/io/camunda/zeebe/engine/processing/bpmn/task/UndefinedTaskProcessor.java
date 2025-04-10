/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.util.Either;

public class UndefinedTaskProcessor implements BpmnElementProcessor<ExecutableActivity> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final BpmnJobBehavior jobBehavior;

  public UndefinedTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    this.stateTransitionBehavior = stateTransitionBehavior;
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    jobBehavior = bpmnBehaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableActivity> getType() {
    return ExecutableActivity.class;
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableActivity element, final BpmnElementContext context) {
    final var activated =
        stateTransitionBehavior.transitionToActivated(context, element.getEventType());
    stateTransitionBehavior.completeElement(activated);
    return SUCCESS;
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableActivity element, final BpmnElementContext context) {
    compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(completed);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            });
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableActivity element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    final var terminated =
        stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
    incidentBehavior.resolveIncidents(context);
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
  }
}
