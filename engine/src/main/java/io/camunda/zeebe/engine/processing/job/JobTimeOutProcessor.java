/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class JobTimeOutProcessor implements CommandProcessor<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to time out activated job with key '%d', but %s";
  private final JobState jobState;
  private final JobMetrics jobMetrics;
  private final BpmnJobActivationBehavior jobActivationBehavior;

  public JobTimeOutProcessor(
      final ProcessingState state,
      final JobMetrics jobMetrics,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    jobState = state.getJobState();
    this.jobMetrics = jobMetrics;
    this.jobActivationBehavior = jobActivationBehavior;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobState.State state = jobState.getState(jobKey);

    if (state == State.ACTIVATED) {
      final JobRecord timedOutJob = command.getValue();

      commandControl.accept(JobIntent.TIMED_OUT, timedOutJob);
      jobMetrics.jobTimedOut(timedOutJob.getType());
      jobActivationBehavior.publishWork(jobKey, timedOutJob);
    } else {
      final String textState;

      switch (state) {
        case ACTIVATABLE:
          textState = "it must be activated first";
          break;
        case FAILED:
          textState = "it is marked as failed";
          break;
        default:
          textState = "no such job was found";
          break;
      }

      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NOT_ACTIVATED_JOB_MESSAGE, jobKey, textState));
    }
    return true;
  }
}
