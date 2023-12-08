/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

public final class UserTaskCompleteProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private final UserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;
  private final EventHandle eventHandle;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskCompleteProcessor(
      final ProcessingState state, final EventHandle eventHandle, final Writers writers) {
    userTaskState = state.getUserTaskState();
    elementInstanceState = state.getElementInstanceState();
    this.eventHandle = eventHandle;
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATED), "complete", userTaskState);
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> userTaskRecord) {
    preconditionChecker
        .check(userTaskRecord)
        .ifRightOrLeft(
            ok -> completeUserTask(userTaskRecord),
            violation -> {
              rejectionWriter.appendRejection(
                  userTaskRecord, violation.getLeft(), violation.getRight());
              responseWriter.writeRejectionOnCommand(
                  userTaskRecord, violation.getLeft(), violation.getRight());
            });
  }

  private void completeUserTask(final TypedRecord<UserTaskRecord> userTaskRecord) {
    final long userTaskKey = userTaskRecord.getKey();
    final UserTaskRecord persistedUserTask =
        userTaskState.getUserTask(userTaskKey, userTaskRecord.getAuthorizations());

    persistedUserTask.setVariables(userTaskRecord.getValue().getVariablesBuffer());

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.COMPLETING, persistedUserTask);
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.COMPLETED, persistedUserTask);
    completeElementInstance(persistedUserTask);
    responseWriter.writeEventOnCommand(
        userTaskKey, UserTaskIntent.COMPLETED, persistedUserTask, userTaskRecord);
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
