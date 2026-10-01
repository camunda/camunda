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
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

/**
 * Expires a standalone job that no worker answered in time: removes it and answers the client that
 * created it with the job, whose {@code worker} tells whether a worker ever activated it.
 */
@ExcludeAuthorizationCheck
public final class StandaloneJobExpireProcessor implements TypedRecordProcessor<JobRecord> {

  private final JobState jobState;
  private final AsyncRequestState asyncRequestState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final JobProcessingMetrics jobMetrics;
  private final InstantSource clock;

  public StandaloneJobExpireProcessor(
      final ProcessingState state,
      final Writers writers,
      final JobProcessingMetrics jobMetrics,
      final InstantSource clock) {
    jobState = state.getJobState();
    asyncRequestState = state.getAsyncRequestState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.jobMetrics = jobMetrics;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();
    final JobRecord job = jobState.getJob(jobKey);
    if (!command.isInternalCommand() || job == null || !job.isStandalone()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to expire standalone job with key '%d', but no such job was found"
              .formatted(jobKey));
      return;
    }
    if (job.getExpiresAt() > clock.millis()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          "Expected to expire standalone job with key '%d', but it expires only at '%d'"
              .formatted(jobKey, job.getExpiresAt()));
      return;
    }

    stateWriter.appendFollowUpEvent(jobKey, JobIntent.EXPIRED, job);
    jobMetrics.countJobEvent(JobAction.EXPIRED, job.getJobKind(), job.getType());

    asyncRequestState
        .findRequest(jobKey, ValueType.JOB, JobIntent.CREATE)
        .ifPresent(
            request -> {
              responseWriter.writeAcceptedResponse(
                  jobKey,
                  JobIntent.EXPIRED,
                  job,
                  ValueType.JOB,
                  request.requestId(),
                  request.requestStreamId());
              stateWriter.appendFollowUpEvent(
                  request.key(), AsyncRequestIntent.PROCESSED, request.record());
            });
  }
}
