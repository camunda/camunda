/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.bpmn.task;

import io.camunda.zeebe.engine.common.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.common.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.common.processing.common.Failure;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.util.Either;

/**
 * A BPMN processor for tasks that are based on jobs and should be processed by job workers. For
 * example, service tasks.
 */
public final class JobWorkerTaskProcessor implements BpmnElementProcessor<ExecutableJobWorkerTask> {

  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;

  public JobWorkerTaskProcessor(
      final BpmnBehaviors behaviors, final BpmnStateTransitionBehavior stateTransitionBehavior) {
    eventSubscriptionBehavior = behaviors.eventSubscriptionBehavior();
    incidentBehavior = behaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = behaviors.variableMappingBehavior();
    jobBehavior = behaviors.jobBehavior();
    stateBehavior = behaviors.stateBehavior();
    compensationSubscriptionBehaviour = behaviors.compensationSubscriptionBehaviour();
  }

  @Override
  public Class<ExecutableJobWorkerTask> getType() {
    return ExecutableJobWorkerTask.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    return variableMappingBehavior.applyInputMappings(context, element);
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    return jobBehavior
        .evaluateJobExpressions(element.getJobWorkerProperties(), context)
        .flatMap(j -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> j))
        .thenDo(
            jobProperties -> {
              jobBehavior.createNewJob(context, element, jobProperties);
              stateTransitionBehavior.transitionToActivated(context, element.getEventType());
            });
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyOutputMappings(context, element)
        .thenDo(ok -> eventSubscriptionBehavior.unsubscribeFromEvents(context));
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(completed);
              stateTransitionBehavior
                  .executeRuntimeInstructions(element, completed)
                  .ifRight(
                      notInterrupted ->
                          stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
            });
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);
    // cancel any active job associated with the task element being terminated
    // (e.g. execution listener or BPMN element job)
    jobBehavior.cancelJob(context);
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

    return TransitionOutcome.CONTINUE;
  }

  @Override
  public void finalizeTermination(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    stateTransitionBehavior.executeRuntimeInstructions(element, context);
  }
}
