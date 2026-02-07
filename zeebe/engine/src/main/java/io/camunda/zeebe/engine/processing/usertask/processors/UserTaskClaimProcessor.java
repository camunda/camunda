/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationHelper.buildProcessDefinitionUpdateUserTaskRequest;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationHelper.buildUserTaskRequest;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandHelper.enrichCommandForRejection;

import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class UserTaskClaimProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "claim";

  private static final String INVALID_USER_TASK_ASSIGNEE_MESSAGE =
      "Expected to claim user task with key '%d', but it has already been assigned";
  private static final String INVALID_USER_TASK_EMPTY_ASSIGNEE_MESSAGE =
      "Expected to claim user task with key '%d', but provided assignee is empty";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final AsyncRequestState asyncRequestState;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final UserTaskCommandPreconditionValidator commandChecker;

  public UserTaskClaimProcessor(
      final ProcessingState state,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    asyncRequestState = state.getAsyncRequestState();
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.authCheckBehavior = authCheckBehavior;
    commandChecker =
        new UserTaskCommandPreconditionValidator(
            List.of(LifecycleState.CREATED), "claim", state.getUserTaskState(), authCheckBehavior);
  }

  @Override
  public Either<Rejection, UserTaskRecord> validateCommand(
      final TypedRecord<UserTaskRecord> command) {
    return commandChecker
        .checkUserTaskExists(command)
        .flatMap(userTask -> enrichCommandForRejection(command, userTask))
        .flatMap(userTask -> checkAuthorization(command, userTask))
        .flatMap(userTask -> commandChecker.checkLifecycleState(command, userTask))
        .flatMap(userTask -> checkClaim(command, userTask));
  }

  @Override
  public void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    asyncRequestBehavior.writeAsyncRequestReceived(userTaskRecord.getElementInstanceKey(), command);

    final var newAssignee = command.getValue().getAssignee();
    if (!userTaskRecord.getAssignee().equals(newAssignee)) {
      userTaskRecord.setAssignee(newAssignee);
      userTaskRecord.setAssigneeChanged();
    }
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(command.getKey(), UserTaskIntent.CLAIMING, userTaskRecord);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    final var asyncRequest =
        asyncRequestState.findRequest(
            userTaskRecord.getElementInstanceKey(), ValueType.USER_TASK, UserTaskIntent.CLAIM);

    if (asyncRequest.isEmpty()) {
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord, command);
      return;
    }

    final var request = asyncRequest.get();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord);
    responseWriter.writeResponse(
        userTaskKey,
        UserTaskIntent.ASSIGNED,
        userTaskRecord,
        ValueType.USER_TASK,
        request.requestId(),
        request.requestStreamId());
    stateWriter.appendFollowUpEvent(request.key(), AsyncRequestIntent.PROCESSED, request.record());
  }

  private Either<Rejection, UserTaskRecord> checkAuthorization(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord persistedUserTask) {
    return authCheckBehavior
        .isAnyAuthorizedOrInternalCommand(
            buildProcessDefinitionUpdateUserTaskRequest(command, persistedUserTask),
            buildUserTaskRequest(command, persistedUserTask, PermissionType.UPDATE),
            buildUserTaskRequest(command, persistedUserTask, PermissionType.CLAIM))
        .map(ignored -> persistedUserTask);
  }

  private static Either<Rejection, UserTaskRecord> checkClaim(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {

    final long userTaskKey = command.getKey();
    final String newAssignee = command.getValue().getAssignee();
    if (newAssignee.isBlank()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_EMPTY_ASSIGNEE_MESSAGE, userTaskKey)));
    }

    final String currentAssignee = userTaskRecord.getAssignee();
    final boolean canClaim = currentAssignee.isBlank() || currentAssignee.equals(newAssignee);
    if (!canClaim) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_ASSIGNEE_MESSAGE, userTaskKey)));
    }

    return Either.right(userTaskRecord);
  }
}
