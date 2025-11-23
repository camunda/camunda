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
import io.camunda.zeebe.stream.api.state.KeyGenerator;

@ExcludeAuthorizationCheck
public final class JobCancelProcessor implements TypedRecordProcessor<JobRecord> {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to cancel job with key '%d', but no such job was found";
  private final JobState jobState;
  private final JobProcessingMetrics jobMetrics;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public JobCancelProcessor(
      final ProcessingState state,
      final JobProcessingMetrics jobMetrics,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    jobState = state.getJobState();
    this.jobMetrics = jobMetrics;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final var jobKey = command.getKey();
    final JobRecord job = jobState.getJob(jobKey);
    if (job == null) {
      final RejectionType rejectionType = RejectionType.NOT_FOUND;
      final String rejectionReason = String.format(NO_JOB_FOUND_MESSAGE, jobKey);
      rejectionWriter.appendRejection(command, rejectionType, rejectionReason);
      if (command.hasRequestMetadata()) {
        responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
      }
    } else {
      // Note that this logic is duplicated in BpmnJobBehavior, if you change this please change
      // it there as well.
      final var entityKey = jobKey < 0 ? keyGenerator.nextKey() : jobKey;

      stateWriter.appendFollowUpEvent(entityKey, JobIntent.CANCELED, job);
      if (command.hasRequestMetadata()) {
        responseWriter.writeEventOnCommand(entityKey, JobIntent.CANCELED, job, command);
      }
      jobMetrics.countJobEvent(JobAction.CANCELED, job.getJobKind(), job.getType());
    }
  }
}
