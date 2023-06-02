/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

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

public class JobRecurProcessor implements TypedRecordProcessor<JobRecord> {

  private static final String NOT_FAILED_JOB_MESSAGE =
      "Expected to back off failed job with key '%d', but %s";
  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final BpmnJobActivationBehavior jobActivationBehavior;

  public JobRecurProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    jobState = processingState.getJobState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.jobActivationBehavior = jobActivationBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    if (state == State.FAILED) {
      final JobRecord recurredJob = jobState.getJob(jobKey);

      stateWriter.appendFollowUpEvent(jobKey, JobIntent.RECURRED_AFTER_BACKOFF, recurredJob);

      jobActivationBehavior.publishWork(jobKey, recurredJob);
    } else {
      final String textState;

      switch (state) {
        case ACTIVATABLE:
          textState = "it is already activable";
          break;
        case ACTIVATED:
          textState = "it is already activated";
          break;
        case ERROR_THROWN:
          textState = "it is in error state";
          break;
        default:
          textState = "no such job was found";
          break;
      }

      final String errorMesage = String.format(NOT_FAILED_JOB_MESSAGE, jobKey, textState);
      rejectionWriter.appendRejection(record, RejectionType.NOT_FOUND, errorMesage);
    }
  }
}
