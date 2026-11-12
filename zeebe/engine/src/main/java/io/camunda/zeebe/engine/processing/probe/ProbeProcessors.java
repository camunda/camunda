/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.ProbeNotifier;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.function.Supplier;

public final class ProbeProcessors {

  private ProbeProcessors() {}

  public static void addProbeProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ExpressionLanguage expressionLanguage,
      final ExpressionProcessor expressionProcessor,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck,
      final AsyncRequestBehavior asyncRequestBehavior,
      final ProbeNotifier probeNotifier,
      final InstantSource clock) {
    final var probeBehavior =
        new ProbeBehavior(probeNotifier, processingState.getAsyncRequestState(), writers);
    final var timeoutChecker =
        new ProbeTimeoutCheckScheduler(scheduledTaskStateFactory.get().getProbeState(), clock);

    typedRecordProcessors
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.CREATE,
            new ProbeCreateProcessor(
                expressionLanguage,
                // the processor of input mappings, without a process scope
                expressionProcessor.withSecretReferenceContext(),
                processingState.getClusterVariableState(),
                cslCheck,
                probeBehavior,
                timeoutChecker,
                asyncRequestBehavior,
                keyGenerator,
                writers,
                clock))
        .onCommand(
            ValueType.PROBE_BATCH,
            ProbeBatchIntent.ACTIVATE,
            new ProbeBatchActivateProcessor(
                processingState.getProbeState(), cslCheck, tenantCheck, keyGenerator, writers))
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.YIELD,
            new ProbeYieldProcessor(
                processingState.getProbeState(), probeBehavior, tenantCheck, writers))
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.ANSWER,
            new ProbeAnswerProcessor(processingState.getProbeState(), cslCheck, writers))
        .onCommand(
            ValueType.PROBE, ProbeIntent.RESPOND, new ProbeRespondProcessor(probeBehavior, writers))
        .onCommand(
            ValueType.PROBE,
            ProbeIntent.TIME_OUT,
            new ProbeTimeOutProcessor(
                processingState.getProbeState(), probeBehavior, writers, clock))
        .withListener(timeoutChecker);
  }
}
