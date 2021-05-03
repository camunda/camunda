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
import io.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableServiceTask;
import io.zeebe.util.Either;
import io.zeebe.util.collection.Tuple;

public final class ServiceTaskProcessor implements BpmnElementProcessor<ExecutableServiceTask> {

  private final ExpressionProcessor expressionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnJobBehavior jobBehavior;

  public ServiceTaskProcessor(final BpmnBehaviors behaviors) {
    eventSubscriptionBehavior = behaviors.eventSubscriptionBehavior();
    expressionBehavior = behaviors.expressionBehavior();
    incidentBehavior = behaviors.incidentBehavior();
    stateTransitionBehavior = behaviors.stateTransitionBehavior();
    variableMappingBehavior = behaviors.variableMappingBehavior();
    stateBehavior = behaviors.stateBehavior();
    jobBehavior = behaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableServiceTask> getType() {
    return ExecutableServiceTask.class;
  }

  @Override
  public void onActivate(final ExecutableServiceTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, context))
        .flatMap(ok -> evaluateJobExpressions(element, context))
        .ifRightOrLeft(
            jobTypeAndRetries -> {
              jobBehavior.createNewJob(
                  context,
                  element,
                  jobTypeAndRetries.getLeft(),
                  jobTypeAndRetries.getRight().intValue());
              stateTransitionBehavior.transitionToActivated(context);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onComplete(final ExecutableServiceTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .ifRightOrLeft(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(context);
              final var completed =
                  stateTransitionBehavior.transitionToCompletedWithParentNotification(
                      element, context);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onTerminate(final ExecutableServiceTask element, final BpmnElementContext context) {

    final var elementInstance = stateBehavior.getElementInstance(context);
    final long jobKey = elementInstance.getJobKey();
    if (jobKey > 0) {
      jobBehavior.cancelJob(jobKey);
      incidentBehavior.resolveJobIncident(jobKey);
    }

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);

    eventSubscriptionBehavior
        .findEventTrigger(context)
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated = stateTransitionBehavior.transitionToTerminated(context);
              eventSubscriptionBehavior.activateTriggeredEvent(
                  context.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
            },
            () -> {
              final var terminated = stateTransitionBehavior.transitionToTerminated(context);
              stateTransitionBehavior.onElementTerminated(element, terminated);
            });
  }

  private Either<Failure, Tuple<String, Long>> evaluateJobExpressions(
      final ExecutableServiceTask element, final BpmnElementContext context) {
    final var scopeKey = context.getElementInstanceKey();

    return expressionBehavior
        .evaluateStringExpression(element.getType(), scopeKey)
        .flatMap(
            jobType ->
                expressionBehavior
                    .evaluateLongExpression(element.getRetries(), scopeKey)
                    .map(retries -> new Tuple<>(jobType, retries)));
  }
}
