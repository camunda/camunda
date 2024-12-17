/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class UserTaskCompleteProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "complete";

  private final ElementInstanceState elementInstanceState;
  private final UserTaskState userTaskState;
  private final EventHandle eventHandle;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskCompleteProcessor(
      final ProcessingState state,
      final EventHandle eventHandle,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    elementInstanceState = state.getElementInstanceState();
    userTaskState = state.getUserTaskState();
    this.eventHandle = eventHandle;
    stateWriter = writers.state();
    commandWriter = writers.command();
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATED),
            "complete",
            state.getUserTaskState(),
            authCheckBehavior);
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

    userTaskRecord.setVariables(command.getValue().getVariablesBuffer());
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.COMPLETING, userTaskRecord);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    if (command.hasRequestMetadata()) {
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord);
      completeElementInstance(userTaskRecord);

      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord, command);
    } else {
      /*
       * If the request metadata is not present in the received command, it indicates that
       * "complete" task listeners were configured, and that the normal flow of the "COMPLETE"
       * command was interrupted by the "COMPLETE_TASK_LISTENER" command.
       * In this case, we need to use the `requestId` and `requestStreamId` from the persisted
       * metadata, which refers to the original "COMPLETE" command, to correctly write the
       * response back.
       *
       * Note: It's important to retrieve this metadata from the user task state before appending
       * the "COMPLETED" event, as it will be cleared by the "COMPLETED" event applier.
       */
      final var recordRequestMetadata = userTaskState.findRecordRequestMetadata(userTaskKey);
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.COMPLETED, userTaskRecord);
      completeElementInstance(userTaskRecord);

      recordRequestMetadata.ifPresent(
          metadata ->
              responseWriter.writeResponse(
                  userTaskKey,
                  UserTaskIntent.COMPLETED,
                  userTaskRecord,
                  ValueType.USER_TASK,
                  metadata.getRequestId(),
                  metadata.getRequestStreamId()));
    }
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
