/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationHelper.buildProcessDefinitionRequest;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationHelper.buildUserTaskRequest;
import static io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandHelper.enrichCommandForRejection;

import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class UserTaskCompleteProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "complete";

  private final ElementInstanceState elementInstanceState;
  private final AsyncRequestState asyncRequestState;
  private final EventHandle eventHandle;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionValidator commandChecker;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public UserTaskCompleteProcessor(
      final ProcessingState state,
      final EventHandle eventHandle,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    elementInstanceState = state.getElementInstanceState();
    asyncRequestState = state.getAsyncRequestState();
    this.eventHandle = eventHandle;
    stateWriter = writers.state();
    commandWriter = writers.command();
    responseWriter = writers.response();
    commandChecker =
        new UserTaskCommandPreconditionValidator(
            List.of(LifecycleState.CREATED),
            "complete",
            state.getUserTaskState(),
            authCheckBehavior);
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public Either<Rejection, UserTaskRecord> validateCommand(
      final TypedRecord<UserTaskRecord> command) {
    return commandChecker
        .checkUserTaskExists(command)
        .flatMap(userTask -> enrichCommandForRejection(command, userTask))
        .flatMap(userTask -> checkAuthorization(command, userTask))
        .flatMap(userTask -> commandChecker.checkLifecycleState(command, userTask));
  }

  @Override
  public void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    asyncRequestBehavior.writeAsyncRequestReceived(userTaskRecord.getElementInstanceKey(), command);

    userTaskRecord.setVariables(command.getValue().getVariablesBuffer());
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(command.getKey(), UserTaskIntent.COMPLETING, userTaskRecord);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {

    final var asyncRequest =
        asyncRequestState.findRequest(
            userTaskRecord.getElementInstanceKey(), ValueType.USER_TASK, UserTaskIntent.COMPLETE);

    final long userTaskKey = command.getKey();
    if (asyncRequest.isEmpty()) {
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord);
      completeElementInstance(userTaskRecord);

      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord, command);
      return;
    }

    final var request = asyncRequest.get();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord);
    completeElementInstance(userTaskRecord);
    responseWriter.writeResponse(
        userTaskKey,
        UserTaskIntent.COMPLETED,
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
            buildProcessDefinitionRequest(
                command, persistedUserTask, PermissionType.UPDATE_USER_TASK),
            buildProcessDefinitionRequest(
                command, persistedUserTask, PermissionType.COMPLETE_USER_TASK),
            buildUserTaskRequest(command, persistedUserTask, PermissionType.UPDATE),
            buildUserTaskRequest(command, persistedUserTask, PermissionType.COMPLETE))
        .map(ignored -> persistedUserTask);
  }

  private void completeElementInstance(final UserTaskRecord userTaskRecord) {
    final var userTaskElementInstanceKey = userTaskRecord.getElementInstanceKey();

    final ElementInstance userTaskElementInstance =
        elementInstanceState.getInstance(userTaskElementInstanceKey);

    if (userTaskElementInstance != null) {
      final long scopeKey = userTaskElementInstance.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        eventHandle.triggeringProcessEvent(userTaskRecord);
        commandWriter.appendFollowUpCommand(
            userTaskElementInstanceKey,
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            userTaskElementInstance.getValue());
      }
    }
  }
}
