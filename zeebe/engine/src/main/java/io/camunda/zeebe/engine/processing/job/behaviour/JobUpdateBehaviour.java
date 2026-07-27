/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job.behaviour;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.job.JobCommandPreconditionValidator;
import io.camunda.zeebe.engine.processing.job.JobLeaseFencingCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;

public class JobUpdateBehaviour {

  public static final String NO_DEADLINE_FOUND_MESSAGE =
      "Expected to update the timeout of job with key '%d', but it is not active";
  private static final String NEGATIVE_RETRIES_MESSAGE =
      "Expected to update retries for job with key '%d' with a positive amount of retries, "
          + "but the amount given was '%d'";

  private final JobState jobState;
  private final InstantSource clock;
  private final StateWriter stateWriter;
  private final JobCommandPreconditionValidator preconditionChecker;
  private final CslAuthorizationCheck cslCheck;

  public JobUpdateBehaviour(
      final ProcessingState processingStateState,
      final InstantSource clock,
      final CslAuthorizationCheck cslCheck,
      final Writers writers) {
    jobState = processingStateState.getJobState();
    this.clock = clock;
    stateWriter = writers.state();
    preconditionChecker =
        new JobCommandPreconditionValidator(
            jobState,
            processingStateState.getBannedInstanceState(),
            "update",
            List.of(State.ACTIVATABLE, State.ACTIVATED, State.FAILED, State.ERROR_THROWN),
            List.of(JobLeaseFencingCheck.forUpdateCommand()),
            cslCheck);
    this.cslCheck = cslCheck;
  }

  public Either<Rejection, JobRecord> checkJobCommand(final TypedRecord<JobRecord> command) {
    return preconditionChecker.check(command);
  }

  public Either<Rejection, JobRecord> isAuthorized(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    return cslCheck.check(
        command,
        RequiredAuthorization.of(
            b -> b.processDefinition().updateProcessInstance().resourceId(job.getBpmnProcessId())),
        job,
        AuthorizationRejectionMapper.noPrincipal());
  }

  public Optional<String> validateJobRetries(final long jobKey, final int retries) {
    if (retries < 1) {
      return Optional.of(NEGATIVE_RETRIES_MESSAGE.formatted(jobKey, retries));
    }
    return Optional.empty();
  }

  public void applyJobRetries(final long jobKey, final int retries, final JobRecord jobRecord) {
    jobRecord.setRetries(retries);
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.RETRIES_UPDATED, jobRecord);
  }

  public Optional<String> updateJobRetries(
      final long jobKey, final int retries, final JobRecord jobRecord) {
    final Optional<String> error = validateJobRetries(jobKey, retries);
    if (error.isPresent()) {
      return error;
    }
    applyJobRetries(jobKey, retries, jobRecord);
    return Optional.empty();
  }

  public Optional<String> validateJobTimeout(final long jobKey, final JobRecord jobRecord) {
    if (!jobState.jobDeadlineExists(jobKey, jobRecord.getDeadline())) {
      return Optional.of(NO_DEADLINE_FOUND_MESSAGE.formatted(jobKey));
    }
    return Optional.empty();
  }

  public void applyJobTimeout(final long jobKey, final long timeout, final JobRecord jobRecord) {
    final long newDeadline = clock.millis() + timeout;
    jobRecord.setDeadline(newDeadline);
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.TIMEOUT_UPDATED, jobRecord);
  }

  public Optional<String> updateJobTimeout(
      final long jobKey, final long timeout, final JobRecord jobRecord) {
    final Optional<String> error = validateJobTimeout(jobKey, jobRecord);
    if (error.isPresent()) {
      return error;
    }
    applyJobTimeout(jobKey, timeout, jobRecord);
    return Optional.empty();
  }

  public void applyJobPriority(
      final long jobKey, final int newPriority, final JobRecord jobRecord) {
    jobRecord.setPriority(newPriority);
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.PRIORITY_UPDATED, jobRecord);
  }
}
