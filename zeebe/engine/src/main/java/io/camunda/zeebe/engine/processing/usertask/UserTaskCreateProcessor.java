/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class UserTaskCreateProcessor implements TypedRecordProcessor<UserTaskRecord> {

  private static final String DEFAULT_ACTION = "create";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;

  public UserTaskCreateProcessor(final ProcessingState state, final Writers writers) {
    stateWriter = writers.state();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<UserTaskRecord> command) {
    createUserTask(command);
  }

  private void createUserTask(final TypedRecord<UserTaskRecord> command) {
    final long userTaskKey = command.getKey();
    final UserTaskRecord userTaskRecord = command.getValue();
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    // TODO implement logic to read TLs, create job for the first TL (if any)
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
    responseWriter.writeEventOnCommand(
        userTaskKey, UserTaskIntent.CREATED, userTaskRecord, command);
  }
}
