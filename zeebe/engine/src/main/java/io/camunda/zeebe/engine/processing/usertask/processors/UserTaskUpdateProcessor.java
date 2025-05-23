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
import io.camunda.zeebe.engine.state.immutable.TriggeringRecordMetadataState;
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
  private final TriggeringRecordMetadataState recordMetadataState;
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
    recordMetadataState = state.getTriggeringRecordMetadataState();
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

    stateWriter.appendFollowUpEventOnCommand(
        userTaskKey, UserTaskIntent.UPDATING, userTaskRecord, command);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    final long userTaskElementInstanceKey = userTaskRecord.getElementInstanceKey();
    if (userTaskRecord.getChangedAttributes().contains(UserTaskRecord.VARIABLES)) {
      variableBehavior.mergeLocalDocument(
          userTaskElementInstanceKey,
          userTaskRecord.getProcessDefinitionKey(),
          userTaskRecord.getProcessInstanceKey(),
          userTaskRecord.getBpmnProcessIdBuffer(),
          userTaskRecord.getTenantId(),
          command.getValue().getVariablesBuffer());
    }

    if (command.hasRequestMetadata()) {
      stateWriter.appendFollowUpEventOnCommand(
          userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, command);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, command);
    } else {
      // this flow is active if "updating" listeners were involved
      variableState
          .findVariableDocumentState(userTaskElementInstanceKey)
          .ifPresentOrElse(
              variableDocumentState -> {
                // present if update transition was triggered by `VARIABLE_DOCUMENT:UPDATE` command
                stateWriter.appendFollowUpEvent(
                    userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);

                final long variableDocumentKey = variableDocumentState.getKey();
                recordMetadataState
                    .findExact(
                        variableDocumentKey,
                        ValueType.VARIABLE_DOCUMENT,
                        VariableDocumentIntent.UPDATE)
                    .ifPresentOrElse(
                        metadata -> {
                          final var variableDocumentRecord = variableDocumentState.getRecord();
                          stateWriter.appendFollowUpEvent(
                              variableDocumentKey,
                              VariableDocumentIntent.UPDATED,
                              variableDocumentRecord,
                              metadata);
                          responseWriter.writeResponse(
                              variableDocumentKey,
                              VariableDocumentIntent.UPDATED,
                              variableDocumentRecord,
                              metadata);
                        },
                        () -> {
                          throw new IllegalStateException(
                              "Variable document state not found, this should not be the case");
                        });
              },
              () ->
                  recordMetadataState
                      .findExact(userTaskKey, ValueType.USER_TASK, UserTaskIntent.UPDATE)
                      .ifPresentOrElse(
                          // present if transition was triggered by `USER_TASK:UPDATE` command
                          metadata -> {
                            stateWriter.appendFollowUpEvent(
                                userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, metadata);
                            responseWriter.writeResponse(
                                userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, metadata);
                          },
                          () ->
                              stateWriter.appendFollowUpEvent(
                                  userTaskKey, UserTaskIntent.UPDATED, userTaskRecord)));
    }
  }
}
