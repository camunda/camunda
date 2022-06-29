/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Builders;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.function.Consumer;

public final class JobEventProcessors {

  public static void addJobProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableZeebeState zeebeState,
      final Consumer<String> onJobsAvailableCallback,
      final BpmnEventPublicationBehavior eventPublicationBehavior,
      final Builders builders,
      final JobMetrics jobMetrics,
      final EventTriggerBehavior eventTriggerBehavior) {

    final var jobState = zeebeState.getJobState();
    final var keyGenerator = zeebeState.getKeyGenerator();

    final EventHandle eventHandle =
        new EventHandle(
            keyGenerator,
            zeebeState.getEventScopeInstanceState(),
            builders,
            zeebeState.getProcessState(),
            eventTriggerBehavior);

    final var jobBackoffChecker = new JobBackoffChecker(jobState);
    typedRecordProcessors
        .onCommand(
            ValueType.JOB,
            JobIntent.COMPLETE,
            new JobCompleteProcessor(zeebeState, jobMetrics, eventHandle))
        .onCommand(
            ValueType.JOB,
            JobIntent.FAIL,
            new JobFailProcessor(
                zeebeState, zeebeState.getKeyGenerator(), jobMetrics, jobBackoffChecker))
        .onCommand(
            ValueType.JOB,
            JobIntent.THROW_ERROR,
            new JobThrowErrorProcessor(
                zeebeState, eventPublicationBehavior, keyGenerator, jobMetrics))
        .onCommand(
            ValueType.JOB, JobIntent.TIME_OUT, new JobTimeOutProcessor(zeebeState, jobMetrics))
        .onCommand(
            ValueType.JOB, JobIntent.UPDATE_RETRIES, new JobUpdateRetriesProcessor(zeebeState))
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new JobCancelProcessor(zeebeState, jobMetrics))
        .onCommand(ValueType.JOB, JobIntent.RECUR_AFTER_BACKOFF, new JobRecurProcessor(zeebeState))
        .onCommand(
            ValueType.JOB_BATCH,
            JobBatchIntent.ACTIVATE,
            new JobBatchActivateProcessor(
                builders, zeebeState, zeebeState.getKeyGenerator(), jobMetrics))
        .withListener(new JobTimeoutTrigger(jobState))
        .withListener(jobBackoffChecker)
        .withListener(
            new StreamProcessorLifecycleAware() {
              @Override
              public void onRecovered(final ReadonlyProcessingContext context) {
                jobState.setJobsAvailableCallback(onJobsAvailableCallback);
              }
            });
  }
}
