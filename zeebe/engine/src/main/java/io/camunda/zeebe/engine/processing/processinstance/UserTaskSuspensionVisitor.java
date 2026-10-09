/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

/**
 * Writes {@link UserTaskIntent#SUSPENDED} for the element instance's user task, whatever the task's
 * lifecycle state, and buffers a {@link UserTaskIntent#RESUME} command for it. Since the command is
 * buffered before the walk finishes, it drains first on resume.
 */
final class UserTaskSuspensionVisitor implements ElementInstanceSuspensionVisitor {

  private final StateWriter stateWriter;
  private final CommandBufferingBehavior bufferingBehavior;
  private final UserTaskState userTaskState;
  private final UserTaskRecord resumeCommand = new UserTaskRecord();

  UserTaskSuspensionVisitor(
      final StateWriter stateWriter,
      final CommandBufferingBehavior bufferingBehavior,
      final UserTaskState userTaskState) {
    this.stateWriter = stateWriter;
    this.bufferingBehavior = bufferingBehavior;
    this.userTaskState = userTaskState;
  }

  @Override
  public void visit(final ElementInstance elementInstance) {
    final long userTaskKey = elementInstance.getUserTaskKey();
    if (userTaskKey <= 0) {
      return;
    }
    final var userTask = userTaskState.getUserTask(userTaskKey);
    if (userTask == null) {
      return;
    }
    resumeCommand
        .setUserTaskKey(userTaskKey)
        .setElementInstanceKey(elementInstance.getKey())
        .setProcessInstanceKey(userTask.getProcessInstanceKey())
        .setProcessDefinitionKey(userTask.getProcessDefinitionKey())
        .setTenantId(userTask.getTenantId());
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.SUSPENDED, userTask);
    bufferingBehavior.bufferCommand(
        userTaskKey,
        ValueType.USER_TASK,
        UserTaskIntent.RESUME,
        resumeCommand,
        userTask.getProcessInstanceKey());
  }
}
