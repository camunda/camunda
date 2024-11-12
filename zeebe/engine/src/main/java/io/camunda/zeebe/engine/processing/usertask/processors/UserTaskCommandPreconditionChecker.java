/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE;

import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public class UserTaskCommandPreconditionChecker {

  private static final String NO_USER_TASK_FOUND_MESSAGE =
      "Expected to %s user task with key '%d', but no such user task was found";
  private static final String INVALID_USER_TASK_STATE_MESSAGE =
      "Expected to %s user task with key '%d', but it is in state '%s'";

  private final List<LifecycleState> validLifecycleStates;
  private final String intent;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final BiFunction<
          TypedRecord<UserTaskRecord>,
          UserTaskRecord,
          Either<Tuple<RejectionType, String>, UserTaskRecord>>
      additionalChecks;
  private final UserTaskState userTaskState;

  public UserTaskCommandPreconditionChecker(
      final List<LifecycleState> validLifecycleStates,
      final String intent,
      final UserTaskState userTaskState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this(validLifecycleStates, intent, null, userTaskState, authCheckBehavior);
  }

  public UserTaskCommandPreconditionChecker(
      final List<LifecycleState> validLifecycleStates,
      final String intent,
      final BiFunction<
              TypedRecord<UserTaskRecord>,
              UserTaskRecord,
              Either<Tuple<RejectionType, String>, UserTaskRecord>>
          additionalChecks,
      final UserTaskState userTaskState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.validLifecycleStates = validLifecycleStates;
    this.intent = intent;
    this.authCheckBehavior = authCheckBehavior;
    this.additionalChecks = additionalChecks;
    this.userTaskState = userTaskState;
  }

  protected Either<Tuple<RejectionType, String>, UserTaskRecord> check(
      final TypedRecord<UserTaskRecord> command) {
    final long userTaskKey = command.getKey();
    final UserTaskRecord persistedRecord =
        userTaskState.getUserTask(userTaskKey, command.getAuthorizations());

    if (persistedRecord == null) {
      return Either.left(
          Tuple.of(
              RejectionType.NOT_FOUND,
              String.format(NO_USER_TASK_FOUND_MESSAGE, intent, userTaskKey)));
    }

    final var authRequest =
        new AuthorizationRequest(
                command, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .addResourceId(persistedRecord.getBpmnProcessId());
    if (!authCheckBehavior.isAuthorized(authRequest)) {
      return Either.left(
          Tuple.of(
              RejectionType.UNAUTHORIZED,
              UNAUTHORIZED_ERROR_MESSAGE.formatted(
                  authRequest.getPermissionType(), authRequest.getResourceType())));
    }

    final LifecycleState lifecycleState = userTaskState.getLifecycleState(userTaskKey);

    if (!validLifecycleStates.contains(lifecycleState)) {
      return Either.left(
          Tuple.of(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_STATE_MESSAGE, intent, userTaskKey, lifecycleState)));
    }

    return Optional.ofNullable(additionalChecks)
        .map(checks -> checks.apply(command, persistedRecord))
        .filter(Either::isLeft)
        .orElse(Either.right(persistedRecord));
  }
}
