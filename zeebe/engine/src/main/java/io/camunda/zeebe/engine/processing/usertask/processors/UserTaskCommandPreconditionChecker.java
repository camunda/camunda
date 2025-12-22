/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class UserTaskCommandPreconditionChecker {

  private static final String NO_USER_TASK_FOUND_MESSAGE =
      "Expected to %s user task with key '%d', but no such user task was found";
  private static final String INVALID_USER_TASK_STATE_MESSAGE =
      "Expected to %s user task with key '%d', but it is in state '%s'";

  private final List<LifecycleState> validLifecycleStates;
  private final String intent;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final UserTaskState userTaskState;

  public UserTaskCommandPreconditionChecker(
      final List<LifecycleState> validLifecycleStates,
      final String intent,
      final UserTaskState userTaskState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.validLifecycleStates = validLifecycleStates;
    this.intent = intent;
    this.authCheckBehavior = authCheckBehavior;
    this.userTaskState = userTaskState;
  }

  protected Either<Rejection, UserTaskRecord> checkUserTaskExists(
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

  // Temporary method to be used until all `UserTaskCommandProcessors` are migrated to provide
  // their own authorization checks
  protected Either<Rejection, UserTaskRecord> checkProcessDefinitionUpdateUserTaskAuth(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord persistedUserTask) {
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_USER_TASK)
            .tenantId(persistedUserTask.getTenantId())
            .addResourceId(persistedUserTask.getBpmnProcessId())
            .build();

    return authCheckBehavior
        .isAuthorizedOrInternalCommand(authRequest)
        .map(ignored -> persistedUserTask);
  }

  protected Either<Rejection, UserTaskRecord> checkLifecycleState(
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
