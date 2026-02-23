/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.time.InstantSource;
import java.util.function.Supplier;

public final class JobEventProcessors {

  public static void addJobProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final JobProcessingMetrics jobMetrics,
      final EngineConfiguration config,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior) {

    final var keyGenerator = processingState.getKeyGenerator();

    final EventHandle eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processingState.getProcessState(),
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());

    final var jobBackoffChecker =
        new JobBackoffCheckScheduler(clock, scheduledTaskStateFactory.get().getJobState());
    typedRecordProcessors
        .onCommand(
            ValueType.JOB,
            JobIntent.COMPLETE,
            new JobCompleteProcessor(
                processingState,
                writers,
                jobMetrics,
                eventHandle,
                authCheckBehavior,
                bpmnBehaviors.variableBehavior()))
        .onCommand(
            ValueType.JOB,
            JobIntent.FAIL,
            new JobFailProcessor(
                processingState,
                writers,
                processingState.getKeyGenerator(),
                jobMetrics,
                jobBackoffChecker,
                bpmnBehaviors,
                authCheckBehavior))
        .onCommand(
            ValueType.JOB,
            JobIntent.YIELD,
            new JobYieldProcessor(processingState, bpmnBehaviors, writers, authCheckBehavior))
        .onCommand(
            ValueType.JOB,
            JobIntent.THROW_ERROR,
            new JobThrowErrorProcessor(
                processingState,
                bpmnBehaviors.eventPublicationBehavior(),
                keyGenerator,
                jobMetrics,
                authCheckBehavior,
                writers))
        .onCommand(
            ValueType.JOB,
            JobIntent.TIME_OUT,
            new JobTimeOutProcessor(
                processingState, writers, jobMetrics, bpmnBehaviors.jobActivationBehavior(), clock))
        .onCommand(
            ValueType.JOB,
            JobIntent.UPDATE_RETRIES,
            new JobUpdateRetriesProcessor(bpmnBehaviors.jobUpdateBehaviour(), writers))
        .onCommand(
            ValueType.JOB,
            JobIntent.UPDATE_TIMEOUT,
            new JobUpdateTimeoutProcessor(bpmnBehaviors.jobUpdateBehaviour(), writers))
        .onCommand(
            ValueType.JOB,
            JobIntent.UPDATE,
            new JobUpdateProcessor(bpmnBehaviors.jobUpdateBehaviour(), writers))
        .onCommand(
            ValueType.JOB,
            JobIntent.CANCEL,
            new JobCancelProcessor(processingState, jobMetrics, writers))
        .onCommand(
            ValueType.JOB,
            JobIntent.RECUR_AFTER_BACKOFF,
            new JobRecurAfterBackoffProcessor(
                processingState, writers, bpmnBehaviors.jobActivationBehavior(), clock))
        .onCommand(
            ValueType.JOB_BATCH,
            JobBatchIntent.ACTIVATE,
            new JobBatchActivateProcessor(
                writers,
                processingState,
                processingState.getKeyGenerator(),
                jobMetrics,
                authCheckBehavior,
                clock))
        .withListener(
            new JobTimeoutCheckScheduler(
                scheduledTaskStateFactory.get().getJobState(),
                config.getJobsTimeoutCheckerPollingInterval(),
                config.getJobsTimeoutCheckerBatchLimit(),
                clock))
        .withListener(jobBackoffChecker);
  }
}
