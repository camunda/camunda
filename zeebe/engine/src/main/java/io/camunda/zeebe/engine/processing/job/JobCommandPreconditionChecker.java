/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class JobCommandPreconditionChecker {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to %s job with key '%d', but no such job was found";
  private static final String INVALID_JOB_STATE_MESSAGE =
      "Expected to %s job with key '%d', but it is in state '%s'";

  private final List<JobState.State> validStates;
  private final String intent;

  public JobCommandPreconditionChecker(
      final String intent, final List<JobState.State> validStates) {
    this.intent = intent;
    this.validStates = validStates;
  }

  protected Either<Tuple<RejectionType, String>, Void> check(final State state, final long jobKey) {
    if (validStates.contains(state)) {
      return Either.right(null);
    }

    if (state == State.NOT_FOUND) {
      return Either.left(
          Tuple.of(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, intent, jobKey)));
    }

    return Either.left(
        Tuple.of(
            RejectionType.INVALID_STATE,
            String.format(INVALID_JOB_STATE_MESSAGE, intent, jobKey, state)));
  }
}
