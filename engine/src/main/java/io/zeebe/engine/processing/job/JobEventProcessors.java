/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import java.util.function.Consumer;

public final class JobEventProcessors {

  public static JobErrorThrownProcessor addJobProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ZeebeState zeebeState,
      final Consumer<String> onJobsAvailableCallback,
      final int maxRecordSize) {

    final var jobState = zeebeState.getJobState();
    final var keyGenerator = zeebeState.getKeyGenerator();

    final var jobErrorThrownProcessor = new JobErrorThrownProcessor(zeebeState);

    typedRecordProcessors
        .onCommand(ValueType.JOB, JobIntent.CREATE, new CreateProcessor())
        .onCommand(ValueType.JOB, JobIntent.COMPLETE, new CompleteProcessor(zeebeState))
        .onCommand(ValueType.JOB, JobIntent.FAIL, new FailProcessor(zeebeState))
        .onCommand(ValueType.JOB, JobIntent.THROW_ERROR, new JobThrowErrorProcessor(jobState))
        .onEvent(ValueType.JOB, JobIntent.ERROR_THROWN, jobErrorThrownProcessor)
        .onCommand(ValueType.JOB, JobIntent.TIME_OUT, new TimeOutProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.UPDATE_RETRIES, new UpdateRetriesProcessor(jobState))
        .onCommand(ValueType.JOB, JobIntent.CANCEL, new CancelProcessor(jobState))
        .onCommand(
            ValueType.JOB_BATCH,
            JobBatchIntent.ACTIVATE,
            new JobBatchActivateProcessor(
                jobState, zeebeState.getVariableState(), keyGenerator, maxRecordSize))
        .withListener(new JobTimeoutTrigger(jobState))
        .withListener(
            new StreamProcessorLifecycleAware() {
              @Override
              public void onRecovered(final ReadonlyProcessingContext context) {
                jobState.setJobsAvailableCallback(onJobsAvailableCallback);
              }
            });

    return jobErrorThrownProcessor;
  }
}
