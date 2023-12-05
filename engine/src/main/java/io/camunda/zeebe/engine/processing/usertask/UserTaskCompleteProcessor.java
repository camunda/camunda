/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;

public final class UserTaskCompleteProcessor implements CommandProcessor<UserTaskRecord> {

  private static final String NO_USER_TASK_FOUND_MESSAGE =
      "Expected to %s user task with key '%d', but no such user task was found";
  private static final String INVALID_USER_TASK_STATE_MESSAGE =
      "Expected to %s user task with key '%d', but it is in state '%s'";
  private final UserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;
  private final EventHandle eventHandle;

  public UserTaskCompleteProcessor(final ProcessingState state, final EventHandle eventHandle) {
    userTaskState = state.getUserTaskState();
    elementInstanceState = state.getElementInstanceState();
    this.eventHandle = eventHandle;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<UserTaskRecord> command,
      final CommandControl<UserTaskRecord> commandControl) {
    final long userTaskKey = command.getKey();
    checkTaskState(userTaskKey)
        .ifRightOrLeft(
            ok -> acceptCommand(command, commandControl),
            violation -> commandControl.reject(violation.getLeft(), violation.getRight()));
    return true;
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long key,
      final Intent intent,
      final UserTaskRecord value) {

    // mark the user task as completed
    stateWriter.appendFollowUpEvent(key, UserTaskIntent.COMPLETED, value);

    // complete the user task element
    final var userTaskElementInstanceKey = value.getElementInstanceKey();

    final ElementInstance userTaskElementInstance =
        elementInstanceState.getInstance(userTaskElementInstanceKey);

    if (userTaskElementInstance != null) {
      final long scopeKey = userTaskElementInstance.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        eventHandle.triggeringProcessEvent(value);
        commandWriter.appendFollowUpCommand(
            userTaskElementInstanceKey,
            ProcessInstanceIntent.COMPLETE_ELEMENT,
            userTaskElementInstance.getValue());
      }
    }
  }

  private Either<Tuple<RejectionType, String>, Void> checkTaskState(final long userTaskKey) {
    final LifecycleState lifecycleState = userTaskState.getLifecycleState(userTaskKey);
    if (lifecycleState == LifecycleState.CREATED) {
      return Either.right(null);
    }

    if (lifecycleState == LifecycleState.NOT_FOUND) {
      return Either.left(
          Tuple.of(
              RejectionType.NOT_FOUND,
              String.format(NO_USER_TASK_FOUND_MESSAGE, UserTaskIntent.COMPLETE, userTaskKey)));
    }

    return Either.left(
        Tuple.of(
            RejectionType.INVALID_STATE,
            String.format(
                INVALID_USER_TASK_STATE_MESSAGE,
                UserTaskIntent.COMPLETE,
                userTaskKey,
                lifecycleState)));
  }

  private void acceptCommand(
      final TypedRecord<UserTaskRecord> command,
      final CommandControl<UserTaskRecord> commandControl) {

    final long userTaskKey = command.getKey();

    final UserTaskRecord userTask =
        userTaskState.getUserTask(userTaskKey, command.getAuthorizations());
    if (userTask == null) {
      commandControl.reject(
          RejectionType.NOT_FOUND,
          String.format(NO_USER_TASK_FOUND_MESSAGE, UserTaskIntent.COMPLETE, userTaskKey));
      return;
    }

    userTask.setVariables(command.getValue().getVariablesBuffer());

    commandControl.accept(UserTaskIntent.COMPLETING, userTask);
  }
}
