/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.event;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.util.Either;

public class StartEventProcessor implements BpmnElementProcessor<ExecutableStartEvent> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnJobBehavior jobBehavior;

  public StartEventProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableStartEvent> getType() {
    return ExecutableStartEvent.class;
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableStartEvent element, final BpmnElementContext context) {
    final var activated =
        stateTransitionBehavior.transitionToActivated(context, element.getEventType());
    stateTransitionBehavior.completeElement(activated);
    return SUCCESS;
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableStartEvent element, final BpmnElementContext context) {

    return variableMappingBehavior.applyOutputMappings(context, element);
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableStartEvent element, final BpmnElementContext context) {
    final var flowScope = (ExecutableCatchEventSupplier) element.getFlowScope();

    final BpmnElementContextImpl flowScopeInstanceContext =
        buildContextForFlowScopeInstance(context);

    return eventSubscriptionBehavior
        .subscribeToEvents(flowScope, flowScopeInstanceContext)
        .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, context))
        .thenDo(completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableStartEvent element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    final var terminated =
        stateTransitionBehavior.transitionToTerminated(context, element.getEventType());

    incidentBehavior.resolveIncidents(terminated);
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
  }

  private BpmnElementContextImpl buildContextForFlowScopeInstance(
      final BpmnElementContext context) {
    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);
    final var flowScopeInstanceContext = new BpmnElementContextImpl();
    flowScopeInstanceContext.init(
        flowScopeInstance.getKey(), flowScopeInstance.getValue(), flowScopeInstance.getState());
    return flowScopeInstanceContext;
  }
}
