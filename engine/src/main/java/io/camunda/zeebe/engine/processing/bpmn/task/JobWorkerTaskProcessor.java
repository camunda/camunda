/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.Loggers;
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
import io.camunda.zeebe.protocol.record.value.ExecutionListenerEventType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * A BPMN processor for tasks that are based on jobs and should be processed by job workers. For
 * example, service tasks.
 */
public final class JobWorkerTaskProcessor implements BpmnElementProcessor<ExecutableJobWorkerTask> {
  private static final Logger LOGGER = Loggers.PROCESS_PROCESSOR_LOGGER;

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
        .flatMap(ignore -> handleStartExecutionListenersOrRegularJob(element, context))
        .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onComplete(final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    handleEndExecutionListenersOrTaskCompletion(element, context)
        .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onExecutionListenerComplete(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {

    final ExecutionListenerEventType eventType =
        stateBehavior.getElementInstance(context).getExecutionListenerEventType();
    final String currentExecutionListenerType =
        stateBehavior.getElementInstance(context).getExecutionListenerType();

    switch (eventType) {
      case START -> {
        final List<ExecutionListener> startExecutionListeners =
            getExecutionListenersByEventType(element, ExecutionListenerEventType.START);
        findNextExecutionListener(startExecutionListeners, currentExecutionListenerType)
            .ifPresentOrElse(
                el -> createExecutionListenerJob(element, context, el),
                () ->
                    regularJobExecution(element, context)
                        .ifLeft(failure -> incidentBehavior.createIncident(failure, context)));
      }
      case END -> {
        final List<ExecutionListener> endExecutionListeners =
            getExecutionListenersByEventType(element, ExecutionListenerEventType.END);
        findNextExecutionListener(endExecutionListeners, currentExecutionListenerType)
            .ifPresentOrElse(
                el -> createExecutionListenerJob(element, context, el),
                () ->
                    regularJobCompletion(element, context)
                        .ifLeft(failure -> incidentBehavior.createIncident(failure, context)));
      }
      case null, default ->
          LOGGER.warn(
              "Unexpected ExecutionListenerEventType='{}' value received",
              eventType); // Create incident also?
    }
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

  private Optional<ExecutionListener> findNextExecutionListener(
      final List<ExecutionListener> listeners, final String currentType) {
    return listeners.stream()
        .dropWhile(el -> !el.getJobWorkerProperties().getType().getExpression().equals(currentType))
        .skip(1) // skip current listener
        .findFirst();
  }

  private List<ExecutionListener> getExecutionListenersByEventType(
      final ExecutableJobWorkerTask element, final ExecutionListenerEventType eventType) {
    return element.getExecutionListeners().stream()
        .filter(el -> eventType == el.getEventType())
        .collect(Collectors.toList());
  }

  private Either<Failure, BpmnElementContext> handleStartExecutionListenersOrRegularJob(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    final List<ExecutionListener> startExecutionListeners =
        getExecutionListenersByEventType(element, ExecutionListenerEventType.START);

    return startExecutionListeners.isEmpty()
        ? regularJobExecution(element, context)
        : createExecutionListenerJob(element, context, startExecutionListeners.getFirst());
  }

  private Either<Failure, BpmnElementContext> regularJobExecution(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    return jobBehavior
        .evaluateJobExpressions(element, context)
        .flatMap(j -> eventSubscriptionBehavior.subscribeToEvents(element, context).map(ok -> j))
        .map(
            jobProperties -> {
              jobBehavior.createNewJob(context, element, jobProperties);
              stateTransitionBehavior.transitionToActivated(context, element.getEventType());
              return context;
            });
  }

  private Either<Failure, BpmnElementContext> handleEndExecutionListenersOrTaskCompletion(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    final List<ExecutionListener> endExecutionListeners =
        getExecutionListenersByEventType(element, ExecutionListenerEventType.END);

    return endExecutionListeners.isEmpty()
        ? regularJobCompletion(element, context)
        : createExecutionListenerJob(element, context, endExecutionListeners.getFirst());
  }

  private Either<Failure, BpmnElementContext> regularJobCompletion(
      final ExecutableJobWorkerTask element, final BpmnElementContext context) {
    return variableMappingBehavior
        .applyOutputMappings(context, element)
        .flatMap(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(context);
              compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
              return stateTransitionBehavior.transitionToCompleted(element, context);
            })
        .flatMap(completionContext -> {
          compensationSubscriptionBehaviour.completeCompensationHandler(context, element);
          stateTransitionBehavior.takeOutgoingSequenceFlows(element, completionContext);
          return Either.right(context);
        });
  }

  private Either<Failure, BpmnElementContext> createExecutionListenerJob(
      final ExecutableJobWorkerTask element,
      final BpmnElementContext context,
      final ExecutionListener listener) {
    return jobBehavior
        .evaluateJobExpressions(listener.getJobWorkerProperties(), context)
        .map(
            elJobProperties -> {
              jobBehavior.createNewExecutionListenerJob(
                  context, element, elJobProperties, listener.getEventType());
              return context;
            });
  }
}
