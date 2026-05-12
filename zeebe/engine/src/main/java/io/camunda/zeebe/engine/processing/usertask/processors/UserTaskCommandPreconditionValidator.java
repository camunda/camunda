/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.BannedInstanceCommandCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.function.Function;

public class UserTaskCommandPreconditionValidator {

  private static final String NO_USER_TASK_FOUND_MESSAGE =
      "Expected to %s user task with key '%d', but no such user task was found";
  private static final String INVALID_USER_TASK_STATE_MESSAGE =
      "Expected to %s user task with key '%d', but it is in state '%s'";

  private final List<LifecycleState> validLifecycleStates;
  private final String intent;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final UserTaskState userTaskState;
  private final BannedInstanceCommandCheck bannedInstanceCheck;

  public UserTaskCommandPreconditionValidator(
      final List<LifecycleState> validLifecycleStates,
      final String intent,
      final UserTaskState userTaskState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final BannedInstanceState bannedInstanceState) {
    this.validLifecycleStates = validLifecycleStates;
    this.intent = intent;
    this.authCheckBehavior = authCheckBehavior;
    this.userTaskState = userTaskState;
    bannedInstanceCheck = new BannedInstanceCommandCheck(bannedInstanceState);
  }

  /**
   * Runs the standard validation chain: existence → rejection enrichment → authorization →
   * lifecycle state → banned instance. The {@code authorizationCheck} step is supplied by the
   * caller because each command type requires different permission checks.
   */
  public Either<Rejection, UserTaskRecord> validate(
      final TypedRecord<UserTaskRecord> command,
      final Function<UserTaskRecord, Either<Rejection, UserTaskRecord>> authorizationCheck) {
    return checkUserTaskExists(command)
        .flatMap(userTask -> UserTaskCommandHelper.enrichCommandForRejection(command, userTask))
        .flatMap(bannedInstanceCheck::check)
        .flatMap(authorizationCheck)
        .flatMap(userTask -> checkLifecycleState(command, userTask));
  }

  public Either<Rejection, UserTaskRecord> checkUserTaskExists(
      final TypedRecord<UserTaskRecord> command) {
    final long userTaskKey = command.getKey();
    final var persistedUserTask =
        userTaskState.getUserTask(userTaskKey, authCheckBehavior.getAuthorizedTenantIds(command));

    if (persistedUserTask == null) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(NO_USER_TASK_FOUND_MESSAGE, intent, userTaskKey)));
    }

    return Either.right(persistedUserTask);
  }

  public Either<Rejection, UserTaskRecord> checkLifecycleState(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord persistedUserTask) {
    final long userTaskKey = command.getKey();
    final LifecycleState lifecycleState = userTaskState.getLifecycleState(userTaskKey);
    if (!validLifecycleStates.contains(lifecycleState)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_STATE_MESSAGE, intent, userTaskKey, lifecycleState)));
    }

    return Either.right(persistedUserTask);
  }
}
