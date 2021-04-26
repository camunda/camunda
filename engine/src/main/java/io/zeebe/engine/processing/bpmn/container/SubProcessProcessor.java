/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.container;

import io.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;

public final class SubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private static final String NO_NONE_START_EVENT_ERROR_MSG =
      "Expected to activate none start event, but no none start event found in sub process";

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public SubProcessProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
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
        .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, activating))
        .ifRightOrLeft(
            ok -> {
              // todo: take a look if activated should be done after
              final var activated = stateTransitionBehavior.transitionToActivated(activating);
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
        .ifRightOrLeft(
            ok -> {
              eventSubscriptionBehavior.unsubscribeFromEvents(completing);
              final var completed =
                  stateTransitionBehavior.transitionToCompletedWithParentNotification(
                      element, completing);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            },
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
  public void beforeExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {}

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
      final BpmnElementContext subProcessContext,
      final BpmnElementContext childContext) {
    if (subProcessContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING) {

      if (childContext == null || stateBehavior.canBeTerminated(childContext)) {
        // if we are able to terminate we try to trigger boundary events
        eventSubscriptionBehavior
            .findEventTrigger(subProcessContext)
            .ifPresentOrElse(
                eventTrigger -> {
                  final var terminated =
                      stateTransitionBehavior.transitionToTerminated(subProcessContext);
                  eventSubscriptionBehavior.activateTriggeredEvent(
                      subProcessContext.getElementInstanceKey(),
                      subProcessContext.getFlowScopeKey(),
                      eventTrigger,
                      terminated);
                },
                () -> {
                  final var terminated =
                      stateTransitionBehavior.transitionToTerminated(subProcessContext);
                  stateTransitionBehavior.onElementTerminated(element, terminated);
                });
      }

    } else {
      // if the flow scope is not terminating we allow
      // * interrupting event sub processes
      // * non interrupting boundary events
      eventSubscriptionBehavior
          .findEventTrigger(subProcessContext)
          .ifPresent(
              eventTrigger ->
                  eventSubscriptionBehavior.activateTriggeredEvent(
                      subProcessContext.getElementInstanceKey(),
                      subProcessContext.getElementInstanceKey(),
                      eventTrigger,
                      subProcessContext));
    }
  }
}
