/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceStateTransitionGuard;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.processing.variable.VariableStateEvaluationContextLookup;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
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

  private final MultiInstanceOutputCollectionBehavior multiInstanceOutputCollectionBehavior;
  private final CatchEventBehavior catchEventBehavior;
  private final EventTriggerBehavior eventTriggerBehavior;
  private final VariableBehavior variableBehavior;
  private final ElementActivationBehavior elementActivationBehavior;

  public BpmnBehaviorsImpl(
      final MutableProcessingState processingState,
      final Writers writers,
      final JobMetrics jobMetrics,
      final DecisionBehavior decisionBehavior,
      final SubscriptionCommandSender subscriptionCommandSender,
      final int partitionsCount,
      final DueDateTimerChecker timerChecker,
      final InstantSource clock) {
    expressionBehavior =
        new ExpressionProcessor(
            ExpressionLanguageFactory.createExpressionLanguage(),
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
            partitionsCount,
            clock);

    eventTriggerBehavior =
        new EventTriggerBehavior(
            processingState.getKeyGenerator(), catchEventBehavior, writers, processingState);

    bpmnDecisionBehavior =
        new BpmnDecisionBehavior(
            decisionBehavior,
            processingState,
            eventTriggerBehavior,
            writers.state(),
            processingState.getKeyGenerator(),
            expressionBehavior);

    stateBehavior = new BpmnStateBehavior(processingState, variableBehavior);

    stateTransitionGuard = new ProcessInstanceStateTransitionGuard(stateBehavior);

    variableMappingBehavior =
        new BpmnVariableMappingBehavior(expressionBehavior, processingState, variableBehavior);

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

    jobBehavior =
        new BpmnJobBehavior(
            processingState.getKeyGenerator(),
            processingState.getJobState(),
            writers,
            expressionBehavior,
            stateBehavior,
            incidentBehavior,
            jobMetrics);

    multiInstanceOutputCollectionBehavior =
        new MultiInstanceOutputCollectionBehavior(stateBehavior, expressionBehavior());

    elementActivationBehavior =
        new ElementActivationBehavior(
            processingState.getKeyGenerator(),
            writers,
            catchEventBehavior,
            processingState.getElementInstanceState());
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
}
