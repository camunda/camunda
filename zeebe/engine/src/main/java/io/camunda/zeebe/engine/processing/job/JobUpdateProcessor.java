/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

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
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class JobUpdateProcessor implements TypedRecordProcessor<JobRecord> {

  private static final String NO_JOB_FOUND_MESSAGE =
      "Expected to update job with key '%d', but no such job was found";
  private static final String NEGATIVE_RETRIES_MESSAGE =
      "Expected to update job with key '%d' with a positive amount of retries, "
          + "but the amount given was '%d'";

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public JobUpdateProcessor(final ProcessingState state, final Writers writers) {
    jobState = state.getJobState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long key = command.getKey();
    final int retries = command.getValue().getRetries();
    final long timeout = command.getValue().getTimeout();
    final var job = jobState.getJob(key, command.getAuthorizations());

    if (job == null) {
      rejectionWriter.appendRejection(
          command, RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(key));
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(key));
      return;
    }

    // Handle retries
    if (retries > 0) {
      job.setRetries(retries);
      stateWriter.appendFollowUpEvent(key, JobIntent.RETRIES_UPDATED, job);
      responseWriter.writeEventOnCommand(key, JobIntent.RETRIES_UPDATED, job, command);
    }
    // Handle timeout
    final long oldDeadline = job.getDeadline();
    if (timeout > -0 && jobState.jobDeadlineExists(key, oldDeadline)) {
      final long newDeadline = ActorClock.currentTimeMillis() + job.getTimeout();
      job.setDeadline(newDeadline);
      stateWriter.appendFollowUpEvent(key, JobIntent.TIMEOUT_UPDATED, job);
      responseWriter.writeEventOnCommand(key, JobIntent.TIMEOUT_UPDATED, job, command);
    }
  }
}
