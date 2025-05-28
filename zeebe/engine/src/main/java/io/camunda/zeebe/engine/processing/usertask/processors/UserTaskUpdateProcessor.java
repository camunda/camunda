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
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserTaskUpdateProcessor implements UserTaskCommandProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskUpdateProcessor.class);
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

    if (command.hasRequestMetadata()) {
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, command);
      return;
    }

    final var recordRequestMetadata = userTaskState.findRecordRequestMetadata(userTaskKey);
    if (recordRequestMetadata.isEmpty()) {
      LOGGER.warn(
          "No request metadata found for userTaskKey={}, writing 'USER_TASK.UPDATED' without response.",
          userTaskKey);
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
      return;
    }

    final var metadata = recordRequestMetadata.get();
    switch (metadata.getTriggerType()) {
      case USER_TASK -> {
        stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
        responseWriter.writeResponse(
            userTaskKey,
            UserTaskIntent.UPDATED,
            userTaskRecord,
            ValueType.USER_TASK,
            metadata.getRequestId(),
            metadata.getRequestStreamId());
      }
      case VARIABLE_DOCUMENT -> {
        // Update triggered by a VariableDocument command.
        // Retrieve the original VariableDocumentRecord to apply correct variable
        // merge logic and write follow-up event.

        final var optionalVariableDocumentState =
            variableState.findVariableDocumentState(userTaskRecord.getElementInstanceKey());
        if (optionalVariableDocumentState.isEmpty()) {
          LOGGER.warn(
              "No VariableDocumentState found for elementInstanceKey={} during task update. "
                  + "Skipping variable merge, only writing 'USER_TASK.UPDATED'.",
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
            variableDocumentKey, VariableDocumentIntent.UPDATED, variableDocumentRecord);

        responseWriter.writeResponse(
            variableDocumentKey,
            VariableDocumentIntent.UPDATED,
            variableDocumentRecord,
            ValueType.VARIABLE_DOCUMENT,
            metadata.getRequestId(),
            metadata.getRequestStreamId());
      }
      default ->
          throw new IllegalArgumentException(
              "Unexpected user task transition trigger type: '%s'"
                  .formatted(metadata.getTriggerType()));
    }
  }

  private void mergeVariables(
      final UserTaskRecord userTaskRecord, final VariableDocumentRecord variableRecord) {
    switch (variableRecord.getUpdateSemantics()) {
      case LOCAL ->
          variableBehavior.mergeLocalDocument(
              userTaskRecord.getElementInstanceKey(),
              userTaskRecord.getProcessDefinitionKey(),
              userTaskRecord.getProcessInstanceKey(),
              userTaskRecord.getBpmnProcessIdBuffer(),
              userTaskRecord.getTenantId(),
              variableRecord.getVariablesBuffer());
      case PROPAGATE ->
          variableBehavior.mergeDocument(
              userTaskRecord.getElementInstanceKey(),
              userTaskRecord.getProcessDefinitionKey(),
              userTaskRecord.getProcessInstanceKey(),
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
