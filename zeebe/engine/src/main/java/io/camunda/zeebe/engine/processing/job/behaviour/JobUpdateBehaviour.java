/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job.behaviour;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.job.JobCommandPreconditionValidator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
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
  private final AuthorizationCheckBehavior authCheckBehavior;

  public JobUpdateBehaviour(
      final ProcessingState processingStateState,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior,
      final Writers writers) {
    jobState = processingStateState.getJobState();
    this.clock = clock;
    stateWriter = writers.state();
    preconditionChecker =
        new JobCommandPreconditionValidator(
            jobState,
            processingStateState.getBannedInstanceState(),
            "update",
            List.of(),
            List.of(this::isAuthorized),
            authCheckBehavior);
    this.authCheckBehavior = authCheckBehavior;
  }

  public Either<Rejection, JobRecord> checkJobCommand(final TypedRecord<JobRecord> command) {
    return preconditionChecker.check(command);
  }

  private Either<Rejection, JobRecord> isAuthorized(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    if (authCheckBehavior.shouldSkipAllChecks()) {
      return Either.right(job);
    }
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(job.getTenantId())
            .addResourceId(job.getBpmnProcessId())
            .build();
    return authCheckBehavior.isAuthorizedOrInternalCommand(authRequest).map(unused -> job);
  }

  public Optional<String> updateJobRetries(
      final long jobKey, final int retries, final JobRecord jobRecord) {
    if (retries < 1) {
      return Optional.of(NEGATIVE_RETRIES_MESSAGE.formatted(jobKey, retries));
    }
    // update retries for response sent to client
    jobRecord.setRetries(retries);
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.RETRIES_UPDATED, jobRecord);
    return Optional.empty();
  }

  public Optional<String> updateJobTimeout(
      final long jobKey, final long timeout, final JobRecord jobRecord) {
    final long oldDeadline = jobRecord.getDeadline();

    if (!jobState.jobDeadlineExists(jobKey, oldDeadline)) {
      return Optional.of(NO_DEADLINE_FOUND_MESSAGE.formatted(jobKey));
    }
    final long newDeadline = clock.millis() + timeout;
    jobRecord.setDeadline(newDeadline);
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.TIMEOUT_UPDATED, jobRecord);
    return Optional.empty();
  }
}
