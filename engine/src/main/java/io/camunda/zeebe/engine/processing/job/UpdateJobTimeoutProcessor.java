/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class UpdateJobTimeoutProcessor implements TypedRecordProcessor<JobRecord> {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to update job deadline with key '%d', but no such job was found";

  public static final String NO_DEADLINE_FOUND_MESSAGE =
      "Expected to find a job with key '%d' and deadline '%d', but no such job was found";

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public UpdateJobTimeoutProcessor(final ProcessingState state, final Writers writers) {
    jobState = state.getJobState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final var value = command.getValue();
    final long jobKey = command.getKey();
    final var job = jobState.getJob(jobKey);

    if (job == null) {
      rejectionWriter.appendRejection(
          command, RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(jobKey));
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(jobKey));
      return;
    }

    final long oldDeadline = job.getDeadline();

    if (!jobState.jobDeadlineExists(jobKey, oldDeadline)) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          NO_DEADLINE_FOUND_MESSAGE.formatted(jobKey, oldDeadline));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.INVALID_STATE,
          NO_DEADLINE_FOUND_MESSAGE.formatted(jobKey, oldDeadline));
      return;
    }

    // check if the job is locked

    // calculate new deadline
    // final long newDeadline = System.currentTimeMillis() + value.getTimeout();

    stateWriter.appendFollowUpEvent(jobKey, JobIntent.TIMEOUT_UPDATED, value);
    responseWriter.writeEventOnCommand(jobKey, JobIntent.TIMEOUT_UPDATED, value, command);
  }
}
