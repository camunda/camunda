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
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.util.Either;
import java.util.List;

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
  public void onActivate(final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyInputMappings(context, element)
        .flatMap(
            ignored -> {
              final List<ExecutionListener> startExecutionListeners =
                  element.getExecutionListeners().stream()
                      .filter(el -> ZeebeExecutionListenerEventType.start.equals(el.getEventType()))
                      .toList();

              if (startExecutionListeners.isEmpty()) {
                return regularJobExecution(element, context);
              } else {
                // create jobs for EL's with 'start' event type
                final ExecutionListener firstExecutionListener = startExecutionListeners.getFirst();
                createExecutionListenerJob(element, context, firstExecutionListener);
                return Either.right(null);
              }
            })
        .ifRightOrLeft(ignore -> {}, failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onComplete(final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    variableMappingBehavior
        .applyOutputMappings(context, element)
        .flatMap(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(context);
              compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
              return stateTransitionBehavior.transitionToCompleted(element, context);
            })
        .ifRightOrLeft(
            completed -> {
              final List<ExecutionListener> endExecutionListeners =
                  element.getExecutionListeners().stream()
                      .filter(el -> ZeebeExecutionListenerEventType.end.equals(el.getEventType()))
                      .toList();

              // create jobs for EL's with 'end' event type
              endExecutionListeners.forEach(
                  listener -> createExecutionListenerJob(element, context, listener));

              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            },
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onTerminate(final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

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
  }

  private Either<Failure, BpmnElementContext> regularJobExecution(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    return jobBehavior
        .evaluateJobExpressions(element, context)
        .flatMap(j -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> j))
        .map(
            jobProperties -> {
              jobBehavior.createNewJob(context, element, jobProperties);
              return stateTransitionBehavior.transitionToActivated(context, element.getEventType());
            });
  }

  private void createExecutionListenerJob(
      final ExecutableJobWorkerTask element,
      final BpmnElementContext context,
      final ExecutionListener listener) {

    jobBehavior
        .evaluateJobExpressions(listener.getJobWorkerProperties(), context)
        .ifRightOrLeft(
            elJobProperties -> jobBehavior.createNewJob(context, element, elJobProperties),
            failure -> incidentBehavior.createIncident(failure, context));
  }
}
