/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

public final class UserTaskAssignProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private final UserTaskState userTaskState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskAssignProcessor(final ProcessingState state, final Writers writers) {
    userTaskState = state.getUserTaskState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATED), "assign", userTaskState);
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> userTaskRecord) {
    preconditionChecker
        .check(userTaskRecord)
        .ifRightOrLeft(
            ok -> assignUserTask(userTaskRecord),
            violation -> {
              rejectionWriter.appendRejection(
                  userTaskRecord, violation.getLeft(), violation.getRight());
              responseWriter.writeRejectionOnCommand(
                  userTaskRecord, violation.getLeft(), violation.getRight());
            });
  }

  private void assignUserTask(final TypedRecord<UserTaskRecord> userTaskRecord) {
    final long userTaskKey = userTaskRecord.getKey();
    final UserTaskRecord persistedUserTask =
        userTaskState.getUserTask(userTaskKey, userTaskRecord.getAuthorizations());

    persistedUserTask.setAssignee(userTaskRecord.getValue().getAssignee());

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNING, persistedUserTask);
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNED, persistedUserTask);
    responseWriter.writeEventOnCommand(
        userTaskKey, UserTaskIntent.ASSIGNED, persistedUserTask, userTaskRecord);
  }
}
