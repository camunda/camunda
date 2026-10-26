/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviorsImpl;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import java.time.InstantSource;
import java.util.function.Supplier;

public final class ProbeEventProcessors {

  private ProbeEventProcessors() {}

  public static void addProbeProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final Writers writers,
      final ProbeBehavior probeBehavior,
      final AsyncRequestBehavior asyncRequestBehavior,
      final CslAuthorizationCheck cslCheck,
      final InstantSource clock) {
    final var timeoutChecker =
        new ProbeTimeoutCheckScheduler(clock, scheduledTaskStateFactory.get().getProbeState());

    typedRecordProcessors
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.CREATE,
            new ProbeCreateProcessor(
                processingState,
                writers,
                processingState.getKeyGenerator(),
                probeBehavior,
                timeoutChecker,
                asyncRequestBehavior,
                bpmnBehaviors.expressionLanguage(),
                bpmnBehaviors.clusterInputExpressionProcessor(),
                cslCheck,
                clock))
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.ANSWER,
            new ProbeAnswerProcessor(processingState, writers, cslCheck))
        .onCommand(
            ValueType.PROBE, ProbeIntent.RESPOND, new ProbeRespondProcessor(probeBehavior, writers))
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.YIELD,
            new ProbeYieldProcessor(processingState, writers, probeBehavior))
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.TIME_OUT,
            new ProbeTimeOutProcessor(processingState, writers, probeBehavior, clock))
        .withListener(timeoutChecker);
  }
}
