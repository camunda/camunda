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
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserTaskUpdateProcessor implements UserTaskCommandProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskUpdateProcessor.class);
  private static final String DEFAULT_ACTION = "update";

  private final StateWriter stateWriter;
  private final VariableState variableState;
  private final AsyncRequestState asyncRequestState;
  private final TypedResponseWriter responseWriter;
  private final VariableBehavior variableBehavior;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final UserTaskCommandPreconditionValidator commandChecker;

  public UserTaskUpdateProcessor(
      final ProcessingState state,
      final Writers writers,
      final VariableBehavior variableBehavior,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    variableState = state.getVariableState();
    asyncRequestState = state.getAsyncRequestState();
    this.variableBehavior = variableBehavior;
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.authCheckBehavior = authCheckBehavior;
    responseWriter = writers.response();
    commandChecker =
        new UserTaskCommandPreconditionValidator(
            List.of(LifecycleState.CREATED), "update", state.getUserTaskState(), authCheckBehavior);
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

    userTaskRecord.wrapChangedAttributesIfValueChanged(command.getValue());
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(command.getKey(), UserTaskIntent.UPDATING, userTaskRecord);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    final var expectedValueTypes = Set.of(ValueType.USER_TASK, ValueType.VARIABLE_DOCUMENT);
    final var asyncRequest =
        asyncRequestState
            .findAllRequestsByScopeKey(userTaskRecord.getElementInstanceKey())
            .filter(request -> expectedValueTypes.contains(request.valueType()))
            // Currently, we assume that at most one async request exists per user task element
            // instance, since only one such request:(e.g., for a UT:UPDATE or VD:UPDATE command)
            // is handled at a time.
            // However, this assumption may need to be revisited if we later support concurrent
            // operations targeting the same user task.
            .findFirst();

    if (asyncRequest.isEmpty()) {
      LOGGER.error(
          "No async request found for userTaskKey='{}', writing 'USER_TASK.UPDATED' without response. "
              + "This may indicate a problem with how the update was triggered. "
              + "If the update was triggered by a user task variables update, variables will not be merged. "
              + "Please report this as a bug.",
          userTaskKey);
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, command);
      return;
    }

    final var request = asyncRequest.get();
    switch (request.valueType()) {
      case USER_TASK -> {
        stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
        responseWriter.writeResponse(
            userTaskKey,
            UserTaskIntent.UPDATED,
            userTaskRecord,
            ValueType.USER_TASK,
            request.requestId(),
            request.requestStreamId());
      }
      case VARIABLE_DOCUMENT -> {
        // Update triggered by a VariableDocument command.
        // Retrieve the original VariableDocumentRecord to apply correct variable
        // merge logic and write follow-up event.
        final var optionalVariableDocumentState =
            variableState.findVariableDocumentState(userTaskRecord.getElementInstanceKey());
        if (optionalVariableDocumentState.isEmpty()) {
          LOGGER.error(
              "No variable document state found for elementInstanceKey='{}' during a task update triggered by a user task variables update. "
                  + "No variables will be merged, and only 'USER_TASK.UPDATED' will be written. "
                  + "This may be caused by a corrupted or incomplete variable update request. "
                  + "Please report this as a bug.",
              userTaskRecord.getElementInstanceKey());
          stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
          return;
        }

        final var variableDocumentState = optionalVariableDocumentState.get();
        final var variableDocumentRecord = variableDocumentState.getRecord();
        mergeVariables(userTaskRecord, variableDocumentRecord);

        // Write follow-up events
        stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
        final long variableDocumentKey = variableDocumentState.getKey();
        stateWriter.appendFollowUpEvent(
            variableDocumentKey,
            VariableDocumentIntent.UPDATED,
            variableDocumentRecord,
            m -> m.operationReference(request.operationReference()));

        responseWriter.writeResponse(
            variableDocumentKey,
            VariableDocumentIntent.UPDATED,
            variableDocumentRecord,
            ValueType.VARIABLE_DOCUMENT,
            request.requestId(),
            request.requestStreamId());
      }
      default ->
          throw new IllegalArgumentException(
              "Unexpected valueType of async request: '%s'".formatted(request.valueType()));
    }

    stateWriter.appendFollowUpEvent(request.key(), AsyncRequestIntent.PROCESSED, request.record());
  }

  private Either<Rejection, UserTaskRecord> checkAuthorization(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord persistedUserTask) {
    return authCheckBehavior
        .isAnyAuthorizedOrInternalCommand(
            buildProcessDefinitionRequest(
                command, persistedUserTask, PermissionType.UPDATE_USER_TASK),
            buildUserTaskRequest(command, persistedUserTask, PermissionType.UPDATE))
        .map(ignored -> persistedUserTask);
  }

  private void mergeVariables(
      final UserTaskRecord userTaskRecord, final VariableDocumentRecord variableRecord) {
    switch (variableRecord.getUpdateSemantics()) {
      case LOCAL ->
          variableBehavior.mergeLocalDocument(
              userTaskRecord.getElementInstanceKey(),
              userTaskRecord.getProcessDefinitionKey(),
              userTaskRecord.getProcessInstanceKey(),
              userTaskRecord.getRootProcessInstanceKey(),
              userTaskRecord.getBpmnProcessIdBuffer(),
              userTaskRecord.getTenantId(),
              variableRecord.getVariablesBuffer());
      case PROPAGATE ->
          variableBehavior.mergeDocument(
              userTaskRecord.getElementInstanceKey(),
              userTaskRecord.getProcessDefinitionKey(),
              userTaskRecord.getProcessInstanceKey(),
              userTaskRecord.getRootProcessInstanceKey(),
              userTaskRecord.getBpmnProcessIdBuffer(),
              userTaskRecord.getTenantId(),
              variableRecord.getVariablesBuffer());
      default ->
          throw new IllegalStateException(
              "Unexpected variable update semantic: '%s'. Expected either 'LOCAL' or 'PROPAGATE'."
                  .formatted(variableRecord.getUpdateSemantics()));
    }
  }
}
