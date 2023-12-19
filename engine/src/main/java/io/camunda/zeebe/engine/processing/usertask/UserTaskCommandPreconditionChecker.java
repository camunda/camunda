/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class UserTaskCommandPreconditionChecker {

  private static final String NO_USER_TASK_FOUND_MESSAGE =
      "Expected to %s user task with key '%d', but no such user task was found";
  private static final String INVALID_USER_TASK_STATE_MESSAGE =
      "Expected to %s user task with key '%d', but it is in state '%s'";
  private static final String INVALID_USER_TASK_ASSIGNEE_MESSAGE =
      "Expected to %s user task with key '%d', but it has already been assigned";

  private final List<LifecycleState> validLifecycleStates;
  private final String intent;

  private final UserTaskState userTaskState;

  public UserTaskCommandPreconditionChecker(
      final List<LifecycleState> validLifecycleStates,
      final String intent,
      final UserTaskState userTaskState) {
    this.validLifecycleStates = validLifecycleStates;
    this.intent = intent;
    this.userTaskState = userTaskState;
  }

  protected Either<Tuple<RejectionType, String>, UserTaskRecord> check(
      final TypedRecord<UserTaskRecord> command) {
    final long userTaskKey = command.getKey();
    final UserTaskRecord persistedRecord =
        userTaskState.getUserTask(userTaskKey, command.getAuthorizations());

    if (persistedRecord == null) {
      return Either.left(
          Tuple.of(
              RejectionType.NOT_FOUND,
              String.format(NO_USER_TASK_FOUND_MESSAGE, intent, userTaskKey)));
    }

    final LifecycleState lifecycleState = userTaskState.getLifecycleState(userTaskKey);

    if (!validLifecycleStates.contains(lifecycleState)) {
      return Either.left(
          Tuple.of(
              RejectionType.INVALID_STATE,
              String.format(INVALID_USER_TASK_STATE_MESSAGE, intent, userTaskKey, lifecycleState)));
    }

    if (intent.equals("claim")) {
      final String assignee = persistedRecord.getAssignee();
      final boolean canClaim =
          assignee.isBlank() || assignee.equals(command.getValue().getAssignee());
      if (!canClaim) {
        return Either.left(
            Tuple.of(
                RejectionType.INVALID_STATE,
                String.format(INVALID_USER_TASK_ASSIGNEE_MESSAGE, intent, userTaskKey)));
      }
    }

    return Either.right(persistedRecord);
  }
}
