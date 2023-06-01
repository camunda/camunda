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
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class JobTimeOutProcessor implements TypedRecordProcessor<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to time out activated job with key '%d', but %s";
  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobMetrics jobMetrics;
  private final BpmnJobActivationBehavior jobActivationBehavior;

  public JobTimeOutProcessor(
      final ProcessingState state,
      final Writers writers,
      final JobMetrics jobMetrics,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    jobState = state.getJobState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.jobMetrics = jobMetrics;
    this.jobActivationBehavior = jobActivationBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    if (state == State.ACTIVATED) {
      final JobRecord timedOutJob = record.getValue();

      stateWriter.appendFollowUpEvent(jobKey, JobIntent.TIMED_OUT, timedOutJob);
      jobMetrics.jobTimedOut(timedOutJob.getType());

      jobActivationBehavior.publishWork(jobKey, timedOutJob);
    } else {
      final var reason = switch (state) {
        case ACTIVATED -> throw new IllegalStateException(
            "This should never happen, if a job is activated it should be timed out not rejected");
        case ACTIVATABLE -> "it must be activated first";
        case FAILED -> "it is marked as failed and is not activated";
        case ERROR_THROWN -> "it has thrown an error and is not activated";
        case NOT_FOUND -> "no such job was found";
      };

      final String errorMessage = String.format(NOT_ACTIVATED_JOB_MESSAGE, jobKey, reason);
      rejectionWriter.appendRejection(record, RejectionType.NOT_FOUND, errorMessage);
    }
  }
}
