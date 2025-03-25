/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnDecisionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBusinessRuleTask;
import io.camunda.zeebe.util.Either;

public final class BusinessRuleTaskProcessor
    extends JobWorkerTaskSupportingProcessor<ExecutableBusinessRuleTask> {

  private final BpmnDecisionBehavior decisionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final BpmnJobBehavior jobBehavior;

  public BusinessRuleTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    super(bpmnBehaviors, stateTransitionBehavior);
    decisionBehavior = bpmnBehaviors.bpmnDecisionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    jobBehavior = bpmnBehaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableBusinessRuleTask> getType() {
    return ExecutableBusinessRuleTask.class;
  }

  @Override
  protected boolean isJobBehavior(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    if (element.getDecisionId() != null) {
      return false;
    }
    if (element.getJobWorkerProperties() == null) {
      throw new BpmnProcessingException(
          context,
          "Expected to process business rule task, but could not determine processing behavior");
    }
    return true;
  }

  @Override
  public Either<Failure, ?> onActivateInternal(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    return variableMappingBehavior.applyInputMappings(context, element);
  }

  @Override
  protected Either<Failure, ?> onFinalizeActivationInternal(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    return decisionBehavior
        .evaluateDecision(element, context)
        .thenDo(
            ok -> {
              final var activated =
                  stateTransitionBehavior.transitionToActivated(context, element.getEventType());
              stateTransitionBehavior.completeElement(activated);
            });
  }

  @Override
  public Either<Failure, ?> onCompleteInternal(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    return variableMappingBehavior.applyOutputMappings(context, element);
  }

  @Override
  protected Either<Failure, ?> onFinalizeCompletionInternal(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    compensationSubscriptionBehaviour.createCompensationSubscription(element, context);
    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(
            completed -> {
              compensationSubscriptionBehaviour.completeCompensationHandler(completed);
              stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
            });
  }

  @Override
  public TransitionState onTerminateInternal(
      final ExecutableBusinessRuleTask element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

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
    return TransitionState.CONTINUE;
  }
}
