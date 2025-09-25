/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.job;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.common.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.common.processing.common.EventHandle;
import io.camunda.zeebe.engine.common.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.common.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.EventApplierRegistry;
import io.camunda.zeebe.engine.common.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.job.applier.JobCanceledApplier;
import io.camunda.zeebe.engine.job.applier.JobCompletedApplierV2;
import io.camunda.zeebe.engine.job.applier.JobCompletedV1Applier;
import io.camunda.zeebe.engine.job.applier.JobCreatedApplier;
import io.camunda.zeebe.engine.job.applier.JobErrorThrownApplier;
import io.camunda.zeebe.engine.job.applier.JobFailedApplier;
import io.camunda.zeebe.engine.job.applier.JobMigratedApplier;
import io.camunda.zeebe.engine.job.applier.JobRecurredApplier;
import io.camunda.zeebe.engine.job.applier.JobRetriesUpdatedApplier;
import io.camunda.zeebe.engine.job.applier.JobTimedOutApplier;
import io.camunda.zeebe.engine.job.applier.JobTimeoutUpdatedApplier;
import io.camunda.zeebe.engine.job.applier.JobUpdatedApplier;
import io.camunda.zeebe.engine.job.applier.JobYieldedApplier;
import io.camunda.zeebe.engine.job.processing.JobBackoffChecker;
import io.camunda.zeebe.engine.job.processing.JobBatchActivateProcessor;
import io.camunda.zeebe.engine.job.processing.JobCancelProcessor;
import io.camunda.zeebe.engine.job.processing.JobCompleteProcessor;
import io.camunda.zeebe.engine.job.processing.JobFailProcessor;
import io.camunda.zeebe.engine.job.processing.JobRecurProcessor;
import io.camunda.zeebe.engine.job.processing.JobThrowErrorProcessor;
import io.camunda.zeebe.engine.job.processing.JobTimeOutProcessor;
import io.camunda.zeebe.engine.job.processing.JobTimeoutCheckerScheduler;
import io.camunda.zeebe.engine.job.processing.JobUpdateProcessor;
import io.camunda.zeebe.engine.job.processing.JobUpdateRetriesProcessor;
import io.camunda.zeebe.engine.job.processing.JobUpdateTimeoutProcessor;
import io.camunda.zeebe.engine.job.processing.JobYieldProcessor;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.time.InstantSource;
import java.util.function.Supplier;

public class JobType {

  public static void registerEventAppliers(
      final EventApplierRegistry registry, final MutableProcessingState state) {
    registry
        .register(JobIntent.CANCELED, new JobCanceledApplier(state))
        .register(JobIntent.COMPLETED, 1, new JobCompletedV1Applier(state))
        .register(JobIntent.COMPLETED, 2, new JobCompletedApplierV2(state))
        .register(JobIntent.CREATED, new JobCreatedApplier(state))
        .register(JobIntent.ERROR_THROWN, new JobErrorThrownApplier(state))
        .register(JobIntent.FAILED, new JobFailedApplier(state))
        .register(JobIntent.YIELDED, new JobYieldedApplier(state))
        .register(JobIntent.RETRIES_UPDATED, new JobRetriesUpdatedApplier(state))
        .register(JobIntent.TIMED_OUT, new JobTimedOutApplier(state))
        .register(JobIntent.RECURRED_AFTER_BACKOFF, new JobRecurredApplier(state))
        .register(JobIntent.TIMEOUT_UPDATED, new JobTimeoutUpdatedApplier(state))
        .register(JobIntent.UPDATED, new JobUpdatedApplier(state))
        .register(JobIntent.MIGRATED, new JobMigratedApplier(state));
  }

  public static void registerCommandProcessors(
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
        new JobBackoffChecker(clock, scheduledTaskStateFactory.get().getJobState());
    typedRecordProcessors
        .onCommand(
            ValueType.JOB,
            JobIntent.COMPLETE,
            new JobCompleteProcessor(
                processingState,
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
                authCheckBehavior))
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
            ValueType.JOB, JobIntent.CANCEL, new JobCancelProcessor(processingState, jobMetrics))
        .onCommand(
            ValueType.JOB,
            JobIntent.RECUR_AFTER_BACKOFF,
            new JobRecurProcessor(
                processingState, writers, bpmnBehaviors.jobActivationBehavior(), clock))
        .onCommand(
            ValueType.JOB_BATCH,
            JobBatchIntent.ACTIVATE,
            new JobBatchActivateProcessor(
                writers,
                processingState,
                processingState.getKeyGenerator(),
                jobMetrics,
                authCheckBehavior))
        .withListener(
            new JobTimeoutCheckerScheduler(
                scheduledTaskStateFactory.get().getJobState(),
                config.getJobsTimeoutCheckerPollingInterval(),
                config.getJobsTimeoutCheckerBatchLimit(),
                clock))
        .withListener(jobBackoffChecker);
  }
}
