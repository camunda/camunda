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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class UserTaskUpdateProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "update";

  private final StateWriter stateWriter;
  private final UserTaskState userTaskState;
  private final VariableState variableState;
  private final TypedResponseWriter responseWriter;
  private final VariableBehavior variableBehavior;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskUpdateProcessor(
      final ProcessingState state,
      final Writers writers,
      final VariableBehavior variableBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    userTaskState = state.getUserTaskState();
    variableState = state.getVariableState();
    this.variableBehavior = variableBehavior;
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATED), "update", state.getUserTaskState(), authCheckBehavior);
  }

  @Override
  public Either<Rejection, UserTaskRecord> validateCommand(
      final TypedRecord<UserTaskRecord> command) {
    return preconditionChecker.check(command);
  }

  @Override
  public void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    userTaskRecord.wrapChangedAttributesIfValueChanged(command.getValue());
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATING, userTaskRecord);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    final long userTaskElementInstanceKey = userTaskRecord.getElementInstanceKey();
    if (userTaskRecord.getChangedAttributes().contains(UserTaskRecord.VARIABLES)) {
      switch (userTaskRecord.getVariableUpdateSemantics()) {
        case LOCAL ->
            variableBehavior.mergeLocalDocument(
                userTaskElementInstanceKey,
                userTaskRecord.getProcessDefinitionKey(),
                userTaskRecord.getProcessInstanceKey(),
                userTaskRecord.getBpmnProcessIdBuffer(),
                userTaskRecord.getTenantId(),
                command.getValue().getVariablesBuffer());
        case PROPAGATE ->
            variableBehavior.mergeDocument(
                userTaskElementInstanceKey,
                userTaskRecord.getProcessDefinitionKey(),
                userTaskRecord.getProcessInstanceKey(),
                userTaskRecord.getBpmnProcessIdBuffer(),
                userTaskRecord.getTenantId(),
                command.getValue().getVariablesBuffer());
        default ->
            throw new IllegalStateException(
                String.format(
                    "Unexpected variable update semantic: '%s'. A user task update requires either 'LOCAL' or "
                        + "'PROPAGATE'. This value is either uninitialized or not supported.",
                    userTaskRecord.getVariableUpdateSemantics()));
      }
    }

    if (command.hasRequestMetadata()) {
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, command);
    } else {
      final var recordRequestMetadata = userTaskState.findRecordRequestMetadata(userTaskKey);
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
      recordRequestMetadata.ifPresent(
          metadata -> {
            switch (metadata.getTriggerType()) {
              case USER_TASK ->
                  responseWriter.writeResponse(
                      userTaskKey,
                      UserTaskIntent.UPDATED,
                      userTaskRecord,
                      ValueType.USER_TASK,
                      metadata.getRequestId(),
                      metadata.getRequestStreamId());
              case VARIABLE_DOCUMENT ->
                  variableState
                      .findVariableDocumentState(userTaskElementInstanceKey)
                      .ifPresent(
                          variableDocumentState -> {
                            final long variableDocumentKey = variableDocumentState.getKey();
                            final var variableDocumentRecord = variableDocumentState.getRecord();
                            stateWriter.appendFollowUpEvent(
                                variableDocumentKey,
                                VariableDocumentIntent.UPDATED,
                                variableDocumentRecord);

                            responseWriter.writeResponse(
                                variableDocumentKey,
                                VariableDocumentIntent.UPDATED,
                                variableDocumentRecord,
                                ValueType.VARIABLE_DOCUMENT,
                                metadata.getRequestId(),
                                metadata.getRequestStreamId());
                          });
              default ->
                  throw new IllegalArgumentException(
                      "Unexpected user task transition trigger type: '%s'"
                          .formatted(metadata.getTriggerType()));
            }
          });
    }
  }
}
