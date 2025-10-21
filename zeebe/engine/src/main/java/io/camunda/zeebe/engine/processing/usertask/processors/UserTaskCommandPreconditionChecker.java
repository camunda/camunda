/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.Rejection;
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
import java.util.List;
import java.util.Map;
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
          TypedRecord<UserTaskRecord>, UserTaskRecord, Either<Rejection, UserTaskRecord>>
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
              TypedRecord<UserTaskRecord>, UserTaskRecord, Either<Rejection, UserTaskRecord>>
          additionalChecks,
      final UserTaskState userTaskState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.validLifecycleStates = validLifecycleStates;
    this.intent = intent;
    this.authCheckBehavior = authCheckBehavior;
    this.additionalChecks = additionalChecks;
    this.userTaskState = userTaskState;
  }

  protected Either<Rejection, UserTaskRecord> check(final TypedRecord<UserTaskRecord> command) {
    final long userTaskKey = command.getKey();
    final var persistedRecord =
        userTaskState.getUserTask(userTaskKey, authCheckBehavior.getAuthorizedTenantIds(command));

    if (persistedRecord == null) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(NO_USER_TASK_FOUND_MESSAGE, intent, userTaskKey)));
    }

    final var isAuthorized =
        authCheckBehavior.isAnyAuthorized(
            new AuthorizationRequest(
                    command,
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    PermissionType.UPDATE_USER_TASK,
                    persistedRecord.getTenantId())
                .addResourceId(persistedRecord.getBpmnProcessId())
                .build(),
            new AuthorizationRequest(
                    command,
                    AuthorizationResourceType.USER_TASK,
                    PermissionType.UPDATE,
                    persistedRecord.getTenantId())
                .addResourceId(Long.toString(persistedRecord.getUserTaskKey()))
                .addResourceProperties(
                    Map.of(
                        UserTaskRecord.ASSIGNEE, persistedRecord.getAssignee(),
                        UserTaskRecord.CANDIDATE_USERS, persistedRecord.getCandidateUsersList(),
                        UserTaskRecord.CANDIDATE_GROUPS, persistedRecord.getCandidateGroupsList()))
                .build());
    if (isAuthorized.isLeft()) {
      return Either.left(isAuthorized.getLeft());
    }

    final LifecycleState lifecycleState = userTaskState.getLifecycleState(userTaskKey);

    if (!validLifecycleStates.contains(lifecycleState)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_STATE_MESSAGE, intent, userTaskKey, lifecycleState)));
    }

    return Optional.ofNullable(additionalChecks)
        .map(checks -> checks.apply(command, persistedRecord))
        .filter(Either::isLeft)
        .orElse(Either.right(persistedRecord));
  }
}
