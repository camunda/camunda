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

  public UserTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    super(bpmnBehaviors, stateTransitionBehavior);
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    userTaskBehavior = bpmnBehaviors.userTaskBehavior();
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
    // TODO will be added with increment 3 of https://github.com/camunda/zeebe/issues/14938
  }

  @Override
  protected void onTerminateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    // TODO will be added with increment 2 of https://github.com/camunda/zeebe/issues/14938
  }
}
