/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public final class JobCancelProcessor implements TypedRecordProcessor<JobRecord> {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to cancel job with key '%d', but no such job was found";
  private final JobState jobState;
  private final JobProcessingMetrics jobMetrics;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public JobCancelProcessor(
      final ProcessingState state, final JobProcessingMetrics jobMetrics, final Writers writers) {
    jobState = state.getJobState();
    this.jobMetrics = jobMetrics;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobRecord job = jobState.getJob(jobKey);
    if (job != null) {
      // Note that this logic is duplicated in BpmnJobBehavior, if you change this please change
      // it there as well.
      stateWriter.appendFollowUpEvent(jobKey, JobIntent.CANCELED, job);
      responseWriter.writeEventOnCommand(jobKey, JobIntent.CANCELED, job, record);
      jobMetrics.countJobEvent(JobAction.CANCELED, job.getJobKind(), job.getType());
    } else {
      rejectionWriter.appendRejection(
          record, RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(jobKey));
    }
  }
}
