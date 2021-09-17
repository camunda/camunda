/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceStateTransitionGuard;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffects;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Function;

public final class BpmnBehaviorsImpl implements BpmnBehaviors {

  private final ExpressionProcessor expressionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final ProcessInstanceStateTransitionGuard stateTransitionGuard;
  private final TypedStreamWriter streamWriter;
  private final BpmnProcessResultSenderBehavior processResultSenderBehavior;
  private final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final StateWriter stateWriter;

  public BpmnBehaviorsImpl(
      final ExpressionProcessor expressionBehavior,
      final TypedStreamWriter streamWriter,
      final TypedResponseWriter responseWriter,
      final SideEffects sideEffects,
      final MutableZeebeState zeebeState,
      final CatchEventBehavior catchEventBehavior,
      final VariableBehavior variableBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final Function<BpmnElementType, BpmnElementContainerProcessor<ExecutableFlowElement>>
          processorLookup,
      final Writers writers,
      final JobMetrics jobMetrics) {

    stateWriter = writers.state();
    final var commandWriter = writers.command();
    this.streamWriter = streamWriter;
    this.expressionBehavior = expressionBehavior;

    stateBehavior = new BpmnStateBehavior(zeebeState, variableBehavior);
    stateTransitionGuard = new ProcessInstanceStateTransitionGuard(stateBehavior);
    variableMappingBehavior =
        new BpmnVariableMappingBehavior(expressionBehavior, zeebeState, variableBehavior);
    stateTransitionBehavior =
        new BpmnStateTransitionBehavior(
            zeebeState.getKeyGenerator(),
            stateBehavior,
            new ProcessEngineMetrics(zeebeState.getPartitionId()),
            processorLookup,
            writers,
            zeebeState.getElementInstanceState());
    eventSubscriptionBehavior =
        new BpmnEventSubscriptionBehavior(
            catchEventBehavior,
            eventTriggerBehavior,
            commandWriter,
            sideEffects,
            zeebeState,
            zeebeState.getKeyGenerator());
    incidentBehavior =
        new BpmnIncidentBehavior(zeebeState, zeebeState.getKeyGenerator(), stateWriter);
    eventPublicationBehavior =
        new BpmnEventPublicationBehavior(
            zeebeState, zeebeState.getKeyGenerator(), eventTriggerBehavior, writers);
    processResultSenderBehavior = new BpmnProcessResultSenderBehavior(zeebeState, responseWriter);
    bufferedMessageStartEventBehavior =
        new BpmnBufferedMessageStartEventBehavior(
            zeebeState, zeebeState.getKeyGenerator(), eventTriggerBehavior, writers);
    jobBehavior =
        new BpmnJobBehavior(
            zeebeState.getKeyGenerator(),
            zeebeState.getJobState(),
            writers,
            expressionBehavior,
            stateBehavior,
            incidentBehavior,
            jobMetrics);
  }

  @Override
  public ExpressionProcessor expressionBehavior() {
    return expressionBehavior;
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
  public TypedCommandWriter commandWriter() {
    return streamWriter;
  }

  @Override
  public BpmnStateTransitionBehavior stateTransitionBehavior() {
    return stateTransitionBehavior;
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
}
