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
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

public final class UserTaskUpdateProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskUpdateProcessor(final ProcessingState state, final Writers writers) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATED), "update", state.getUserTaskState());
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> command) {
    preconditionChecker
        .check(command)
        .ifRightOrLeft(
            persistedRecord -> updateUserTask(command, persistedRecord),
            violation -> {
              rejectionWriter.appendRejection(command, violation.getLeft(), violation.getRight());
              responseWriter.writeRejectionOnCommand(
                  command, violation.getLeft(), violation.getRight());
            });
  }

  private void updateUserTask(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    userTaskRecord.wrapChangedAttributes(command.getValue(), true);

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATING, userTaskRecord);
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.UPDATED, userTaskRecord);
    responseWriter.writeEventOnCommand(
        userTaskKey, UserTaskIntent.UPDATED, userTaskRecord, command);
  }
}
