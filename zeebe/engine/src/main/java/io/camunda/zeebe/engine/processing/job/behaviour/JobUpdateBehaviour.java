/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job.behaviour;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.Optional;

public class JobUpdateBehaviour {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to update job with key '%d', but no such job was found";
  public static final String NO_DEADLINE_FOUND_MESSAGE =
      "Expected to update the timeout of job with key '%d', but it is not active";
  private static final String NEGATIVE_RETRIES_MESSAGE =
      "Expected to update retries for job with key '%d' with a positive amount of retries, "
          + "but the amount given was '%d'";

  private final JobState jobState;
  private final InstantSource clock;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public JobUpdateBehaviour(
      final JobState jobState,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.jobState = jobState;
    this.clock = clock;
    this.authCheckBehavior = authCheckBehavior;
  }

  public Either<Rejection, JobRecord> getJob(
      final long jobKey, final TypedRecord<JobRecord> command) {
    final var job = jobState.getJob(jobKey, command.getAuthorizations());

    if (job != null) {
      return Either.right(job);
    }
    return Either.left(
        new Rejection(RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(jobKey)));
  }

  public Either<Rejection, JobRecord> isAuthorized(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    final var authRequest =
        new AuthorizationRequest(
                command, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .addResourceId(job.getBpmnProcessId());

    if (!authCheckBehavior.isAuthorized(authRequest)) {
      return Either.left(
          new Rejection(
              RejectionType.UNAUTHORIZED,
              AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
                  authRequest.getPermissionType(),
                  authRequest.getResourceType(),
                  "BPMN process id '%s'".formatted(job.getBpmnProcessId()))));
    }
    return Either.right(job);
  }

  public Optional<String> updateJobRetries(
      final long jobKey, final int retries, final JobRecord jobRecord) {
    if (retries < 1) {
      return Optional.of(NEGATIVE_RETRIES_MESSAGE.formatted(jobKey, retries));
    }
    // update retries for response sent to client
    jobRecord.setRetries(retries);
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
    return Optional.empty();
  }
}
