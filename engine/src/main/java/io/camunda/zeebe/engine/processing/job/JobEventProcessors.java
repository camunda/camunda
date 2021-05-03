/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import java.util.function.Consumer;

public final class JobEventProcessors {

  public static void addJobProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableZeebeState zeebeState,
      final Consumer<String> onJobsAvailableCallback,
      final BpmnEventPublicationBehavior eventPublicationBehavior,
      final int maxRecordSize,
      final Writers writers) {

    final var jobState = zeebeState.getJobState();
    final var keyGenerator = zeebeState.getKeyGenerator();

    typedRecordProcessors
        .onCommand(ValueType.JOB, JobIntent.COMPLETE, new JobCompleteProcessor(zeebeState))
        .onCommand(
            ValueType.JOB,
            JobIntent.FAIL,
            new JobFailProcessor(zeebeState, zeebeState.getKeyGenerator()))
        .onCommand(
            ValueType.JOB,
            JobIntent.THROW_ERROR,
            new JobThrowErrorProcessor(zeebeState, eventPublicationBehavior, keyGenerator))
        .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new JobTimeOutProcessor(zeebeState))
        .onCommand(
            ValueType.JOB, JobIntent.UPDATE_RETRIES, new JobUpdateRetriesProcessor(zeebeState))
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new JobCancelProcessor(zeebeState))
        .onCommand(
            ValueType.JOB_BATCH,
            JobBatchIntent.ACTIVATE,
            new JobBatchActivateProcessor(
                writers, zeebeState, zeebeState.getKeyGenerator(), maxRecordSize))
        .withListener(new JobTimeoutTrigger(jobState))
        .withListener(
            new StreamProcessorLifecycleAware() {
              @Override
              public void onRecovered(final ReadonlyProcessingContext context) {
                jobState.setJobsAvailableCallback(onJobsAvailableCallback);
              }
            });
  }
}
