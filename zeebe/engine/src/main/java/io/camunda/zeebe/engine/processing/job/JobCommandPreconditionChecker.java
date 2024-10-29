/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.Rejection;
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

  public JobCommandPreconditionChecker(
      final JobState jobState, final String intent, final List<State> validStates) {
    this(jobState, intent, validStates, List.of());
  }

  public JobCommandPreconditionChecker(
      final JobState jobState,
      final String intent,
      final List<State> validStates,
      final List<JobCommandCheck> customChecks) {
    this.jobState = jobState;
    this.intent = intent;
    this.validStates = validStates;
    this.customChecks = customChecks;
  }

  protected Either<Rejection, JobRecord> check(
      final State state, final TypedRecord<JobRecord> command) {
    final var persistedJob = jobState.getJob(command.getKey(), command.getAuthorizations());

    if (state == State.NOT_FOUND || persistedJob == null) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(NO_JOB_FOUND_MESSAGE, intent, command.getKey())));
    }

    if (!validStates.contains(state)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(INVALID_JOB_STATE_MESSAGE, intent, command.getKey(), state)));
    }

    return customChecks.stream()
        .map(check -> check.check(command, persistedJob))
        .filter(Either::isLeft)
        .findFirst()
        .orElse(Either.right(persistedJob));
  }
}
