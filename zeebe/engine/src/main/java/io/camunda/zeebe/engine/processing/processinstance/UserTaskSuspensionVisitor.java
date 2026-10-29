/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

/**
 * Writes {@link UserTaskIntent#SUSPENDED} for the element instance's user task, whatever the task's
 * lifecycle state. Its applier tracks the task as suspended, and {@link
 * ProcessInstanceResumeUserTasksProcessor} resumes the tracked tasks before {@link
 * ProcessInstanceIntent#RESUMED}.
 *
 * <p>Terminating element instances are skipped: their user task is about to be canceled. Already
 * suspended user tasks are skipped too, so overlapping walks suspend each task once.
 */
final class UserTaskSuspensionVisitor implements ElementInstanceVisitor {

  private final StateWriter stateWriter;
  private final UserTaskState userTaskState;
  private final SuspensionMetrics suspensionMetrics;

  UserTaskSuspensionVisitor(
      final StateWriter stateWriter,
      final UserTaskState userTaskState,
      final SuspensionMetrics suspensionMetrics) {
    this.stateWriter = stateWriter;
    this.userTaskState = userTaskState;
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public boolean shouldVisit(final ElementInstance elementInstance) {
    final long userTaskKey = elementInstance.getUserTaskKey();
    return userTaskKey > 0
        && !elementInstance.isTerminating()
        && !userTaskState.isSuspended(
            elementInstance.getValue().getProcessInstanceKey(), userTaskKey);
  }

  @Override
  public void visit(final ElementInstance elementInstance) {
    final long userTaskKey = elementInstance.getUserTaskKey();
    final var userTask = userTaskState.getUserTask(userTaskKey);
    if (userTask == null) {
      return;
    }
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.SUSPENDED, userTask);
    suspensionMetrics.userTaskSuspended();
  }
}
