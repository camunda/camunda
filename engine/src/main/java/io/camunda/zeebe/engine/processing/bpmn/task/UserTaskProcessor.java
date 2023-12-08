/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;

public final class UserTaskProcessor extends JobWorkerTaskSupportingProcessor<ExecutableUserTask> {

  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnUserTaskBehavior userTaskBehavior;
  private final BpmnStateBehavior stateBehavior;

  public UserTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    super(bpmnBehaviors, stateTransitionBehavior);
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    userTaskBehavior = bpmnBehaviors.userTaskBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
  }

  @Override
  public Class<ExecutableUserTask> getType() {
    return ExecutableUserTask.class;
  }

  @Override
  protected boolean isJobBehavior(
      final ExecutableUserTask element, final BpmnElementContext context) {
    if (element.getUserTaskProperties() != null) {
      return false;
    }
    if (element.getJobWorkerProperties() == null) {
      throw new BpmnProcessingException(
          context, "Expected to process user task, but could not determine processing behavior");
    }
    return true;
  }

  @Override
  protected void onActivateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> userTaskBehavior.evaluateUserTaskExpressions(element, context))
        .flatMap(j -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> j))
        .ifRightOrLeft(
            userTaskProperties -> {
              final var userTaskKey =
                  userTaskBehavior.createNewUserTask(context, element, userTaskProperties);
              userTaskBehavior.userTaskCreated(userTaskKey, context, element, userTaskProperties);
              stateTransitionBehavior.transitionToActivated(context, element.getEventType());
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  protected void onCompleteInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .flatMap(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(context);
              return stateTransitionBehavior.transitionToCompleted(element, context);
            })
        .ifRightOrLeft(
            completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  protected void onTerminateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

    userTaskBehavior.cancelUserTask(context);
    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);

    eventSubscriptionBehavior
        .findEventTrigger(context)
        .filter(eventTrigger -> flowScopeInstance.isActive())
        .filter(eventTrigger -> !flowScopeInstance.isInterrupted())
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
              eventSubscriptionBehavior.activateTriggeredEvent(
                  context.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
            },
            () -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
              stateTransitionBehavior.onElementTerminated(element, terminated);
            });
  }
}
