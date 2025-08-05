/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceStateTransitionGuard;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.processing.variable.VariableStateEvaluationContextLookup;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import java.time.InstantSource;

public final class BpmnBehaviorsImpl implements BpmnBehaviors {

  private final ExpressionProcessor expressionBehavior;
  private final BpmnDecisionBehavior bpmnDecisionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final ProcessInstanceStateTransitionGuard stateTransitionGuard;
  private final BpmnProcessResultSenderBehavior processResultSenderBehavior;
  private final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final MultiInstanceInputCollectionBehavior multiInstanceInputCollectionBehavior;
  private final MultiInstanceOutputCollectionBehavior multiInstanceOutputCollectionBehavior;
  private final CatchEventBehavior catchEventBehavior;
  private final EventTriggerBehavior eventTriggerBehavior;
  private final VariableBehavior variableBehavior;
  private final ElementActivationBehavior elementActivationBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final BpmnSignalBehavior signalBehavior;
  private final BpmnUserTaskBehavior userTaskBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final JobUpdateBehaviour jobUpdateBehaviour;
  private final BpmnAdHocSubProcessBehavior adHocSubProcessBehavior;

  public BpmnBehaviorsImpl(
      final MutableProcessingState processingState,
      final Writers writers,
      final JobProcessingMetrics jobMetrics,
      final DecisionBehavior decisionBehavior,
      final SubscriptionCommandSender subscriptionCommandSender,
      final RoutingInfo routingInfo,
      final DueDateTimerChecker timerChecker,
      final JobStreamer jobStreamer,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    expressionBehavior =
        new ExpressionProcessor(
            ExpressionLanguageFactory.createExpressionLanguage(new ZeebeFeelEngineClock(clock)),
            new VariableStateEvaluationContextLookup(processingState.getVariableState()));

    variableBehavior =
        new VariableBehavior(
            processingState.getVariableState(), writers.state(), processingState.getKeyGenerator());

    catchEventBehavior =
        new CatchEventBehavior(
            processingState,
            processingState.getKeyGenerator(),
            expressionBehavior,
            subscriptionCommandSender,
            writers.state(),
            writers.sideEffect(),
            timerChecker,
            routingInfo,
            clock,
            transientProcessMessageSubscriptionState);

    stateBehavior = new BpmnStateBehavior(processingState, variableBehavior);

    eventTriggerBehavior =
        new EventTriggerBehavior(
            processingState.getKeyGenerator(),
            catchEventBehavior,
            writers,
            processingState,
            stateBehavior);

    bpmnDecisionBehavior =
        new BpmnDecisionBehavior(
            decisionBehavior,
            processingState,
            eventTriggerBehavior,
            writers.state(),
            processingState.getKeyGenerator(),
            expressionBehavior,
            stateBehavior);

    stateTransitionGuard = new ProcessInstanceStateTransitionGuard(stateBehavior);

    variableMappingBehavior =
        new BpmnVariableMappingBehavior(
            expressionBehavior, processingState, variableBehavior, eventTriggerBehavior);

    eventSubscriptionBehavior =
        new BpmnEventSubscriptionBehavior(
            catchEventBehavior, eventTriggerBehavior, processingState);

    incidentBehavior =
        new BpmnIncidentBehavior(
            processingState, processingState.getKeyGenerator(), writers.state());

    eventPublicationBehavior =
        new BpmnEventPublicationBehavior(
            processingState,
            processingState.getKeyGenerator(),
            eventTriggerBehavior,
            stateBehavior,
            writers);

    processResultSenderBehavior =
        new BpmnProcessResultSenderBehavior(processingState, writers.response());

    bufferedMessageStartEventBehavior =
        new BpmnBufferedMessageStartEventBehavior(
            processingState,
            processingState.getKeyGenerator(),
            eventTriggerBehavior,
            stateBehavior,
            writers,
            clock);

    jobActivationBehavior =
        new BpmnJobActivationBehavior(
            jobStreamer,
            processingState,
            writers,
            processingState.getKeyGenerator(),
            jobMetrics,
            clock);

    multiInstanceInputCollectionBehavior =
        new MultiInstanceInputCollectionBehavior(
            expressionBehavior, stateBehavior, writers.state());
    multiInstanceOutputCollectionBehavior =
        new MultiInstanceOutputCollectionBehavior(stateBehavior, expressionBehavior());

    elementActivationBehavior =
        new ElementActivationBehavior(
            processingState.getKeyGenerator(),
            writers,
            catchEventBehavior,
            processingState.getElementInstanceState(),
            stateBehavior);

    signalBehavior =
        new BpmnSignalBehavior(
            processingState.getKeyGenerator(),
            processingState.getVariableState(),
            writers,
            expressionBehavior);

    userTaskBehavior =
        new BpmnUserTaskBehavior(
            processingState.getKeyGenerator(),
            writers,
            expressionBehavior,
            stateBehavior,
            processingState.getFormState(),
            processingState.getUserTaskState(),
            processingState.getVariableState(),
            processingState.getAsyncRequestState(),
            clock);

    jobBehavior =
        new BpmnJobBehavior(
            processingState.getKeyGenerator(),
            processingState.getJobState(),
            writers,
            expressionBehavior,
            stateBehavior,
            processingState.getResourceState(),
            incidentBehavior,
            jobActivationBehavior,
            jobMetrics,
            userTaskBehavior);

    compensationSubscriptionBehaviour =
        new BpmnCompensationSubscriptionBehaviour(
            processingState.getKeyGenerator(), processingState, writers, stateBehavior);

    jobUpdateBehaviour =
        new JobUpdateBehaviour(processingState.getJobState(), clock, authCheckBehavior);

    adHocSubProcessBehavior =
        new BpmnAdHocSubProcessBehavior(
            processingState.getKeyGenerator(),
            writers,
            stateBehavior,
            variableBehavior,
            processingState);
  }

  @Override
  public ExpressionProcessor expressionBehavior() {
    return expressionBehavior;
  }

  @Override
  public BpmnDecisionBehavior bpmnDecisionBehavior() {
    return bpmnDecisionBehavior;
  }

  @Override
  public BpmnVariableMappingBehavior variableMappingBehavior() {
    return variableMappingBehavior;
  }

  @Override
  public BpmnEventPublicationBehavior eventPublicationBehavior() {
    return eventPublicationBehavior;
  }

  @Override
  public BpmnEventSubscriptionBehavior eventSubscriptionBehavior() {
    return eventSubscriptionBehavior;
  }

  @Override
  public BpmnIncidentBehavior incidentBehavior() {
    return incidentBehavior;
  }

  @Override
  public BpmnStateBehavior stateBehavior() {
    return stateBehavior;
  }

  @Override
  public ProcessInstanceStateTransitionGuard stateTransitionGuard() {
    return stateTransitionGuard;
  }

  @Override
  public BpmnProcessResultSenderBehavior processResultSenderBehavior() {
    return processResultSenderBehavior;
  }

  @Override
  public BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior() {
    return bufferedMessageStartEventBehavior;
  }

  @Override
  public BpmnJobBehavior jobBehavior() {
    return jobBehavior;
  }

  @Override
  public BpmnSignalBehavior signalBehavior() {
    return signalBehavior;
  }

  @Override
  public MultiInstanceInputCollectionBehavior inputCollectionBehavior() {
    return multiInstanceInputCollectionBehavior;
  }

  @Override
  public MultiInstanceOutputCollectionBehavior outputCollectionBehavior() {
    return multiInstanceOutputCollectionBehavior;
  }

  @Override
  public CatchEventBehavior catchEventBehavior() {
    return catchEventBehavior;
  }

  @Override
  public EventTriggerBehavior eventTriggerBehavior() {
    return eventTriggerBehavior;
  }

  @Override
  public VariableBehavior variableBehavior() {
    return variableBehavior;
  }

  @Override
  public ElementActivationBehavior elementActivationBehavior() {
    return elementActivationBehavior;
  }

  @Override
  public BpmnJobActivationBehavior jobActivationBehavior() {
    return jobActivationBehavior;
  }

  @Override
  public BpmnUserTaskBehavior userTaskBehavior() {
    return userTaskBehavior;
  }

  @Override
  public BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour() {
    return compensationSubscriptionBehaviour;
  }

  @Override
  public JobUpdateBehaviour jobUpdateBehaviour() {
    return jobUpdateBehaviour;
  }

  @Override
  public BpmnAdHocSubProcessBehavior adHocSubProcessBehavior() {
    return adHocSubProcessBehavior;
  }
}
