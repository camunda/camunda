/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.util.Either;

public class AdHocSubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableAdHocSubProcess> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnIncidentBehavior incidentBehavior;

  public AdHocSubProcessProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
  }

  @Override
  public Class<ExecutableAdHocSubProcess> getType() {
    return ExecutableAdHocSubProcess.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {
    return variableMappingBehavior.applyInputMappings(context, element);
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableAdHocSubProcess element, final BpmnElementContext context) {

    return eventSubscriptionBehavior
        .subscribeToEvents(element, context)
        .thenDo(
            ok -> stateTransitionBehavior.transitionToActivated(context, element.getEventType()));
  }

  @Override
  public void onTerminate(
      final ExecutableAdHocSubProcess element, final BpmnElementContext terminating) {

    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(terminating);
    }
    eventSubscriptionBehavior.unsubscribeFromEvents(terminating);
    incidentBehavior.resolveIncidents(terminating);

    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(terminating);

    eventSubscriptionBehavior
        .findEventTrigger(terminating)
        .filter(eventTrigger -> flowScopeInstance.isActive() && !flowScopeInstance.isInterrupted())
        .ifPresentOrElse(
            eventTrigger -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(
                      terminating, element.getEventType());

              eventSubscriptionBehavior.activateTriggeredEvent(
                  terminated.getElementInstanceKey(),
                  terminated.getFlowScopeKey(),
                  eventTrigger,
                  terminated);
            },
            () -> {
              final var terminated =
                  stateTransitionBehavior.transitionToTerminated(
                      terminating, element.getEventType());

              stateTransitionBehavior.onElementTerminated(element, terminated);
            });
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableAdHocSubProcess element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {}

  @Override
  public void onChildTerminated(
      final ExecutableAdHocSubProcess element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {}
}
