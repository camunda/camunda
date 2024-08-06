/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job.behaviour;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class JobUpdateBehaviour {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to update retries for job with key '%d', but no such job was found";
  public static final String NO_JOB_FOUND_MESSAGE_DEADLINE =
      "Expected to update job deadline with key '%d', but no such job was found";
  public static final String NO_DEADLINE_FOUND_MESSAGE =
      "Expected to update the timeout of job with key '%d', but it is not active";
  private static final String NEGATIVE_RETRIES_MESSAGE =
      "Expected to update retries for job with key '%d' with a positive amount of retries, "
          + "but the amount given was '%d'";
  private static final String ERROR_MESSAGE_AT_LEAST_ONE_FIELD =
      "At least one field between retries or timeout is required";

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public JobUpdateBehaviour(final JobState jobState, final Writers writers) {
    this.jobState = jobState;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  public JobRecord getJobOrAppendRejection(
      final long jobKey, final TypedRecord<JobRecord> command) {
    final var job = jobState.getJob(jobKey, command.getAuthorizations());

    if (job != null) {
      return job;
    }
    rejectionWriter.appendRejection(
        command, RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(jobKey));
    responseWriter.writeRejectionOnCommand(
        command, RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(jobKey));
    return null;
  }

  public boolean updateJobRetries(
      final long jobKey, final JobRecord jobRecord, final TypedRecord<JobRecord> command) {
    final int retries = command.getValue().getRetries();
    if (retries < 1) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_ARGUMENT,
          String.format(NEGATIVE_RETRIES_MESSAGE, jobKey, retries));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.INVALID_ARGUMENT,
          String.format(NEGATIVE_RETRIES_MESSAGE, jobKey, retries));
      return false;
    }
    // update retries for response sent to client
    jobRecord.setRetries(retries);
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.RETRIES_UPDATED, jobRecord);
    return true;
  }

  public void writeUpdateJobRetriesResponse(
      final long jobKey, final JobRecord jobRecord, final TypedRecord<JobRecord> command) {
    responseWriter.writeEventOnCommand(jobKey, JobIntent.RETRIES_UPDATED, jobRecord, command);
  }

  public boolean updateJobTimeout(
      final long jobKey, final JobRecord jobRecord, final TypedRecord<JobRecord> command) {
    final long oldDeadline = jobRecord.getDeadline();

    if (!jobState.jobDeadlineExists(jobKey, oldDeadline)) {
      rejectionWriter.appendRejection(
          command, RejectionType.INVALID_STATE, NO_DEADLINE_FOUND_MESSAGE.formatted(jobKey));
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.INVALID_STATE, NO_DEADLINE_FOUND_MESSAGE.formatted(jobKey));
      return false;
    }
    final long timeout = command.getValue().getTimeout();
    final long newDeadline = ActorClock.currentTimeMillis() + timeout;
    jobRecord.setDeadline(newDeadline);
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.TIMEOUT_UPDATED, jobRecord);
    return true;
  }

  public void writeUpdateJobTimeoutResponse(
      final long jobKey, final JobRecord jobRecord, final TypedRecord<JobRecord> command) {
    responseWriter.writeEventOnCommand(jobKey, JobIntent.TIMEOUT_UPDATED, jobRecord, command);
  }

  public void completeJobUpdate(
      final long jobKey, final JobRecord jobRecord, final TypedRecord<JobRecord> command) {
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.UPDATED, jobRecord);
    responseWriter.writeEventOnCommand(jobKey, JobIntent.UPDATED, jobRecord, command);
  }

  public void rejectJobUpdate(final TypedRecord<JobRecord> command) {
    rejectionWriter.appendRejection(
        command, RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_AT_LEAST_ONE_FIELD);
    responseWriter.writeRejectionOnCommand(
        command, RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_AT_LEAST_ONE_FIELD);
  }
}
