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
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TriggeringRecordMetadataState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class UserTaskClaimProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "claim";

  private static final String INVALID_USER_TASK_ASSIGNEE_MESSAGE =
      "Expected to claim user task with key '%d', but it has already been assigned";
  private static final String INVALID_USER_TASK_EMPTY_ASSIGNEE_MESSAGE =
      "Expected to claim user task with key '%d', but provided assignee is empty";

  private final TriggeringRecordMetadataState recordMetadataState;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionChecker preconditionChecker;

  public UserTaskClaimProcessor(
      final ProcessingState state,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    recordMetadataState = state.getTriggeringRecordMetadataState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATED),
            "claim",
            UserTaskClaimProcessor::checkClaim,
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

    final var newAssignee = command.getValue().getAssignee();
    if (!userTaskRecord.getAssignee().equals(newAssignee)) {
      userTaskRecord.setAssignee(newAssignee);
      userTaskRecord.setAssigneeChanged();
    }
    userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION));

    stateWriter.appendFollowUpEventOnCommand(
        userTaskKey, UserTaskIntent.CLAIMING, userTaskRecord, command);
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    if (command.hasRequestMetadata()) {
      stateWriter.appendFollowUpEventOnCommand(
          userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord, command);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord, command);
    } else {
      // this flow is active if "assigning" listeners were involved
      recordMetadataState
          .findExact(userTaskKey, ValueType.USER_TASK, UserTaskIntent.CLAIM)
          .ifPresentOrElse(
              // present if transition was triggered by `USER_TASK:CLAIM` command
              metadata -> {
                stateWriter.appendFollowUpEvent(
                    userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord, metadata);
                responseWriter.writeResponse(
                    userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord, metadata);
              },
              () ->
                  stateWriter.appendFollowUpEvent(
                      userTaskKey, UserTaskIntent.ASSIGNED, userTaskRecord));
    }
  }

  private static Either<Rejection, UserTaskRecord> checkClaim(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {

    final long userTaskKey = command.getKey();
    final String newAssignee = command.getValue().getAssignee();
    if (newAssignee.isBlank()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_EMPTY_ASSIGNEE_MESSAGE, userTaskKey)));
    }

    final String currentAssignee = userTaskRecord.getAssignee();
    final boolean canClaim = currentAssignee.isBlank() || currentAssignee.equals(newAssignee);
    if (!canClaim) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_ASSIGNEE_MESSAGE, userTaskKey)));
    }

    return Either.right(userTaskRecord);
  }
}
