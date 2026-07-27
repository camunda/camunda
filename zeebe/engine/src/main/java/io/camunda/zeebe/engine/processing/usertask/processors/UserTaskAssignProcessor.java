/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck.TaskProperties.ALL;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck.TaskProperties.ASSIGNEE_ONLY;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck.processDefinition;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck.userTask;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;

public final class UserTaskAssignProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "assign";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final AsyncRequestState asyncRequestState;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final UserTaskAuthorizationCheck userTaskAuth;
  private final UserTaskCommandPreconditionValidator commandChecker;

  public UserTaskAssignProcessor(
      final ProcessingState state,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final CslAuthorizationCheck cslCheck,
      final UserTaskAuthorizationCheck userTaskAuth) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    asyncRequestState = state.getAsyncRequestState();
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.userTaskAuth = userTaskAuth;
    commandChecker =
        new UserTaskCommandPreconditionValidator(
            List.of(LifecycleState.CREATED),
            "assign",
            state.getUserTaskState(),
            cslCheck,
            state.getBannedInstanceState());
  }

  @Override
  public Either<Rejection, UserTaskRecord> validateCommand(
      final TypedRecord<UserTaskRecord> command) {
    return commandChecker.validate(command, task -> checkAuthorization(command, task));
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

    stateWriter.appendFollowUpEvent(command.getKey(), UserTaskIntent.ASSIGNING, userTaskRecord);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    final var asyncRequest =
        asyncRequestState.findRequest(
            userTaskRecord.getElementInstanceKey(), ValueType.USER_TASK, UserTaskIntent.ASSIGN);

    if (asyncRequest.isEmpty()) {
      // Async request might be absent if the assignment transition was triggered internally
      // by Zeebe due to an assignee configured in the process model.
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord);
      responseWriter.writeAcceptedResponseOnCommand(
          userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord, command);
      return;
    }

    final var request = asyncRequest.get();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord);
    responseWriter.writeAcceptedResponse(
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
    // Primary checks: PD.UPDATE_USER_TASK || UT.UPDATE (by id or task property)
    final var primary =
        userTaskAuth.check(
            command,
            persistedUserTask,
            processDefinition(PermissionType.UPDATE_USER_TASK),
            userTask(PermissionType.UPDATE, ALL));
    if (primary.isRight() || !isSelfUnassign(command, persistedUserTask)) {
      return primary;
    }

    // Secondary check for self-unassigning: PD.CLAIM_USER_TASK || UT.CLAIM (assignee property only)
    return userTaskAuth.check(
        command,
        persistedUserTask,
        processDefinition(PermissionType.CLAIM_USER_TASK),
        userTask(PermissionType.CLAIM, ASSIGNEE_ONLY));
  }

  private boolean isSelfUnassign(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord persistedUserTask) {
    final var newAssignee = command.getValue().getAssignee();
    final var currentAssignee = persistedUserTask.getAssignee();
    final var currentUsername = getCurrentUsername(command);
    return newAssignee.isEmpty() && currentUsername.filter(currentAssignee::equals).isPresent();
  }

  private Optional<String> getCurrentUsername(final TypedRecord<?> command) {
    return Optional.ofNullable(
        (String) command.getAuthorizations().get(Authorization.AUTHORIZED_USERNAME));
  }
}
