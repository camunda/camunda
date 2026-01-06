/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class JobCommandPreconditionChecker {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to %s job with key '%d', but no such job was found";
  private static final String INVALID_JOB_STATE_MESSAGE =
      "Expected to %s job with key '%d', but it is in state '%s'";

  private final List<JobState.State> validStates;
  private final JobState jobState;
  private final String intent;
  private final List<JobCommandCheck> customChecks;
  private final AuthorizationCheckBehavior authorizationCheckBehavior;

  public JobCommandPreconditionChecker(
      final JobState jobState,
      final String intent,
      final List<State> validStates,
      final AuthorizationCheckBehavior authorizationCheckBehavior) {
    this(jobState, intent, validStates, List.of(), authorizationCheckBehavior);
  }

  public JobCommandPreconditionChecker(
      final JobState jobState,
      final String intent,
      final List<State> validStates,
      final List<JobCommandCheck> customChecks,
      final AuthorizationCheckBehavior authorizationCheckBehavior) {
    this.jobState = jobState;
    this.intent = intent;
    this.validStates = validStates;
    this.customChecks = customChecks;
    this.authorizationCheckBehavior = authorizationCheckBehavior;
  }

  protected Either<Rejection, JobRecord> check(
      final State state, final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();
    final var authorizedTenantIds = authorizationCheckBehavior.getAuthorizedTenantIds(command);
    final var storedJob = jobState.getJob(jobKey, authorizedTenantIds);

    if (state == State.NOT_FOUND || storedJob == null) {
      return Either.left(
          new Rejection(RejectionType.NOT_FOUND, NO_JOB_FOUND_MESSAGE.formatted(intent, jobKey)));
    }

    if (!validStates.contains(state)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              INVALID_JOB_STATE_MESSAGE.formatted(intent, jobKey, state)));
    }

    // Evaluate custom checks on the job `command` and the `job` retrieved from `jobState`.
    // Return the first `failure` encountered, or if all checks pass, return the `job` from
    // `jobState` for further processing.
    return customChecks.stream()
        .map(check -> check.check(command, storedJob))
        .filter(Either::isLeft)
        .findFirst()
        .orElse(Either.right(storedJob));
  }
}
