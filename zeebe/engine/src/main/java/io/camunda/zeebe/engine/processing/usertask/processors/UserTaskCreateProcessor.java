/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import com.google.common.base.Strings;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;

public final class UserTaskCreateProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "create";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskAssignProcessor assignProcessor;

  //  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskCreateProcessor(
      final ProcessingState state,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final UserTaskAssignProcessor assignProcessor) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    responseWriter = writers.response();
    this.assignProcessor = assignProcessor;

    //    preconditionChecker =
    //        new UserTaskCommandPreconditionChecker(
    //            List.of(), "create", state.getUserTaskState(), authCheckBehavior);
  }

  //  @Override
  //  public Either<Tuple<RejectionType, String>, UserTaskRecord> validateCommand(
  //      final TypedRecord<UserTaskRecord> command) {
  //    return preconditionChecker.check(command);
  //  }

  @Override
  public void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    //    userTaskRecord.setAssignee(command.getValue().getAssignee());
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();
    final String assignee = userTaskRecord.getAssignee();

    // ensure that the assignee is not set as part of the CREATED event as this is taken care of by
    // the ASSIGN command.
    userTaskRecord.setAssignee("");

    //    userTaskRecord.setAssignee(command.getValue().getAssignee());
    //    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
    responseWriter.writeEventOnCommand(
        userTaskKey, UserTaskIntent.CREATED, userTaskRecord, command);

    if (!Strings.isNullOrEmpty(assignee)) {
      userTaskRecord.setAssignee(assignee);
      new UnwrittenRecord(
          command.getKey(),
          command.getPartitionId(),
          userTaskRecord,
          new RecordMetadata().copyFrom(command));
      assignProcessor.onCommand(command, userTaskRecord);
      assignProcessor.onFinalizeCommand(command, userTaskRecord);
      //      commandWriter.appendFollowUpCommand(
      //          userTaskKey, UserTaskIntent.ASSIGN, userTaskRecord.setAssignee(assignee));
    }
  }
}
