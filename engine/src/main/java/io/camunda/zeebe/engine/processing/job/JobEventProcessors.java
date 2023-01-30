/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.function.Consumer;

public final class JobEventProcessors {

  public static void addJobProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableZeebeState zeebeState,
      final Consumer<String> onJobsAvailableCallback,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final JobMetrics jobMetrics) {

    final var jobState = zeebeState.getJobState();
    final var keyGenerator = zeebeState.getKeyGenerator();

    final EventHandle eventHandle =
        new EventHandle(
            keyGenerator,
            zeebeState.getEventScopeInstanceState(),
            writers,
            zeebeState.getProcessState(),
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());

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
                zeebeState,
                writers,
                zeebeState.getKeyGenerator(),
                jobMetrics,
                jobBackoffChecker,
                bpmnBehaviors.variableBehavior()))
        .onCommand(
            ValueType.JOB,
            JobIntent.THROW_ERROR,
            new JobThrowErrorProcessor(
                zeebeState, bpmnBehaviors.eventPublicationBehavior(), keyGenerator, jobMetrics))
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
                writers, zeebeState, zeebeState.getKeyGenerator(), jobMetrics))
        .withListener(new JobTimeoutTrigger(jobState))
        .withListener(jobBackoffChecker)
        .withListener(
            new StreamProcessorLifecycleAware() {
              @Override
              public void onRecovered(final ReadonlyStreamProcessorContext context) {
                jobState.setJobsAvailableCallback(onJobsAvailableCallback);
              }
            });
  }
}
