/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE;
import static io.camunda.zeebe.engine.processing.job.JobCommandPreconditionChecker.NO_JOB_FOUND_MESSAGE;

import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

/**
 * Default implementation to process JobCommands to reduce duplication in CommandProcessor
 * implementations.
 */
final class DefaultJobCommandPreconditionGuard {

  private final String intent;
  private final JobState state;
  private final JobAcceptFunction acceptCommand;
  private final JobCommandPreconditionChecker preconditionChecker;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public DefaultJobCommandPreconditionGuard(
      final String intent,
      final JobState state,
      final JobAcceptFunction acceptCommand,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.intent = intent;
    this.state = state;
    this.acceptCommand = acceptCommand;
    preconditionChecker =
        new JobCommandPreconditionChecker(intent, List.of(State.ACTIVATABLE, State.ACTIVATED));
    this.authCheckBehavior = authCheckBehavior;
  }

  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final State jobState = state.getState(jobKey);

    preconditionChecker
        .check(jobState, jobKey)
        .flatMap(unused -> checkAuthorization(command))
        .ifRightOrLeft(
            job -> acceptCommand.accept(command, commandControl, job),
            violation -> commandControl.reject(violation.getLeft(), violation.getRight()));

    return true;
  }

  private Either<Tuple<RejectionType, String>, JobRecord> checkAuthorization(
      final TypedRecord<JobRecord> command) {
    final var jobKey = command.getKey();
    final var job = state.getJob(jobKey, command.getAuthorizations());

    if (job == null) {
      return Either.left(
          Tuple.of(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, intent, jobKey)));
    }

    final var request =
        new AuthorizationRequest(
                command, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .addResourceId(job.getBpmnProcessId());

    if (authCheckBehavior.isAuthorized(request)) {
      return Either.right(job);
    }

    return Either.left(
        Tuple.of(
            RejectionType.UNAUTHORIZED,
            UNAUTHORIZED_ERROR_MESSAGE.formatted(
                request.getPermissionType(), request.getResourceType())));
  }
}
