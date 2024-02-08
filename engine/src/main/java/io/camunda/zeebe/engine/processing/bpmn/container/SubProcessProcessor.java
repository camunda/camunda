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
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;

public final class SubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private static final String NO_NONE_START_EVENT_ERROR_MSG =
      "Expected to activate none start event, but no none start event found in sub process";

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;

  public SubProcessProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
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
              final var activated =
                  stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
              final ExecutableStartEvent startEvent = element.getNoneStartEvent();
              if (startEvent == null) {
                throw new BpmnProcessingException(activated, NO_NONE_START_EVENT_ERROR_MSG);
              }
              stateTransitionBehavior.activateChildInstance(activated, startEvent);
            },
            failure -> incidentBehavior.createIncident(failure, activating));
  }

  @Override
  public void onComplete(
      final ExecutableFlowElementContainer element, final BpmnElementContext completing) {
    variableMappingBehavior
        .applyOutputMappings(completing, element)
        .flatMap(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(completing);
              compensationSubscriptionBehaviour.createCompensationSubscriptionForSubprocess(
                  element, completing);
              return stateTransitionBehavior.transitionToCompleted(element, completing);
            })
        .ifRightOrLeft(
            completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
            failure -> incidentBehavior.createIncident(failure, completing));
  }

  @Override
  public void onTerminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext terminating) {

    eventSubscriptionBehavior.unsubscribeFromEvents(terminating);
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
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {
    if (stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext subProcessContext,
      final BpmnElementContext childContext) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(subProcessContext);
    final var subProcessInstance = stateBehavior.getElementInstance(subProcessContext);

    final boolean interruptedByTerminateEndEvent =
        stateBehavior.isInterruptedByTerminateEndEvent(subProcessContext, subProcessInstance);
    if (stateBehavior.isInterrupted(subProcessContext) && !interruptedByTerminateEndEvent) {
      // an interrupting event subprocess was triggered
      eventSubscriptionBehavior
          .findEventTrigger(subProcessContext)
          .ifPresent(
              eventTrigger ->
                  eventSubscriptionBehavior.activateTriggeredEvent(
                      subProcessContext.getElementInstanceKey(),
                      subProcessContext.getElementInstanceKey(),
                      eventTrigger,
                      subProcessContext));

    } else if (childContext == null || stateBehavior.canBeTerminated(childContext)) {
      // if we are able to terminate we try to trigger boundary events
      eventSubscriptionBehavior
          .findEventTrigger(subProcessContext)
          .filter(eventTrigger -> flowScopeInstance.isActive())
          .filter(eventTrigger -> !flowScopeInstance.isInterrupted())
          .ifPresentOrElse(
              eventTrigger -> {
                final var terminated =
                    stateTransitionBehavior.transitionToTerminated(
                        subProcessContext, element.getEventType());
                eventSubscriptionBehavior.activateTriggeredEvent(
                    subProcessContext.getElementInstanceKey(),
                    subProcessContext.getFlowScopeKey(),
                    eventTrigger,
                    terminated);
              },
              () -> {
                if (subProcessInstance.isTerminating()) {
                  // the subprocess was terminated by its flow scope
                  final var terminated =
                      stateTransitionBehavior.transitionToTerminated(
                          subProcessContext, element.getEventType());
                  stateTransitionBehavior.onElementTerminated(element, terminated);

                } else if (interruptedByTerminateEndEvent) {
                  // the child element instances were terminated by a terminate end event in the
                  // subprocess
                  stateTransitionBehavior.completeElement(subProcessContext);
                }
              });
    }
  }
}
