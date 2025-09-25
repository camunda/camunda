/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.job.processing;

import io.camunda.zeebe.engine.common.processing.Rejection;
import io.camunda.zeebe.engine.common.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.common.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.common.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.job.state.immutable.JobState;
import io.camunda.zeebe.engine.job.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

/**
 * Default implementation to process JobCommands to reduce duplication in CommandProcessor
 * implementations.
 */
public final class DefaultJobCommandPreconditionGuard {

  private final JobState state;
  private final JobAcceptFunction acceptCommand;
  private final JobCommandPreconditionChecker preconditionChecker;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public DefaultJobCommandPreconditionGuard(
      final String intent,
      final JobState state,
      final JobAcceptFunction acceptCommand,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this(intent, state, acceptCommand, authCheckBehavior, List.of());
  }

  public DefaultJobCommandPreconditionGuard(
      final String intent,
      final JobState state,
      final JobAcceptFunction acceptCommand,
      final AuthorizationCheckBehavior authCheckBehavior,
      final List<JobCommandCheck> customChecks) {
    this.state = state;
    this.acceptCommand = acceptCommand;
    this.authCheckBehavior = authCheckBehavior;
    preconditionChecker =
        new JobCommandPreconditionChecker(
            state,
            intent,
            List.of(State.ACTIVATABLE, State.ACTIVATED),
            customChecks,
            authCheckBehavior);
  }

  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final State jobState = state.getState(jobKey);

    preconditionChecker
        .check(jobState, command)
        .flatMap(job -> checkAuthorization(command, job))
        .ifRightOrLeft(
            job -> acceptCommand.accept(command, commandControl, job),
            rejection -> commandControl.reject(rejection.type(), rejection.reason()));

    return true;
  }

  private Either<Rejection, JobRecord> checkAuthorization(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    final var request =
        new AuthorizationRequest(
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE,
                job.getTenantId())
            .addResourceId(job.getBpmnProcessId());
    return authCheckBehavior.isAuthorized(request).map(unused -> job);
  }
}
