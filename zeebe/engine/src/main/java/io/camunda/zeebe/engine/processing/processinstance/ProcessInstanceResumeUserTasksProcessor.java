/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resumes one suspended user task of the process instance per {@code RESUME_USER_TASKS} cycle:
 * finds the next user task tracked as suspended, writes {@link UserTaskIntent#RESUMED} for it, then
 * appends the next {@code RESUME_USER_TASKS} with that task's key as cursor. Once none are left, it
 * appends {@code COMPLETE_RESUMING}. {@link ProcessInstanceResumeJobsProcessor} writes the first
 * cycle once no suspended job is left.
 *
 * <p>One task per cycle, since an instance can have any number of suspended user tasks. The {@code
 * RESUMED} applier stops tracking the task, so the next cycle's inclusive seek from the cursor
 * starts at the following task.
 *
 * <p>Rejected for the same reasons as {@code RESUME_JOBS}: the instance is gone, its suspension
 * marker is no longer {@code RESUMING}, or it is terminating or completing.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceResumeUserTasksProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private static final String INSTANCE_GONE_MESSAGE =
      "Expected to resume the suspended user tasks of process instance '%d', but it no longer "
          + "exists — likely cancelled while resuming.";
  private static final String ALREADY_FINALIZED_MESSAGE =
      "Expected to resume the suspended user tasks of process instance '%d', but its suspension "
          + "marker is no longer RESUMING — likely already finalized by a concurrent resume.";
  private static final String LIFECYCLE_ENDING_MESSAGE =
      "Expected to resume the suspended user tasks of process instance '%d', but it is %s — "
          + "resume is superseded by the ending lifecycle.";
  private static final Set<ProcessInstanceIntent> LIFECYCLE_ENDING_STATES =
      EnumSet.of(
          ProcessInstanceIntent.ELEMENT_TERMINATING, ProcessInstanceIntent.ELEMENT_COMPLETING);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final UserTaskState userTaskState;
  private final SuspensionMetrics suspensionMetrics;

  public ProcessInstanceResumeUserTasksProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final SuspensionMetrics suspensionMetrics) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    suspensionState = processingState.getSuspensionState();
    userTaskState = processingState.getUserTaskState();
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final long processInstanceKey = command.getKey();
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      reject(command, INSTANCE_GONE_MESSAGE.formatted(processInstanceKey));
      return;
    }
    if (suspensionState.getSuspensionState(processInstanceKey) != SuspensionState.State.RESUMING) {
      reject(command, ALREADY_FINALIZED_MESSAGE.formatted(processInstanceKey));
      return;
    }
    final var lifecycleState = processInstance.getState();
    if (LIFECYCLE_ENDING_STATES.contains(lifecycleState)) {
      reject(command, LIFECYCLE_ENDING_MESSAGE.formatted(processInstanceKey, lifecycleState));
      return;
    }

    final long resumedUserTaskKey =
        resumeNextUserTask(processInstanceKey, command.getValue().getResumeFromUserTaskKey());
    final var followUpValue = processInstance.getValue();
    if (resumedUserTaskKey >= 0) {
      followUpValue.setResumeFromUserTaskKey(resumedUserTaskKey);
      commandWriter.appendFollowUpCommand(
          processInstanceKey, ProcessInstanceIntent.RESUME_USER_TASKS, followUpValue);
      suspensionMetrics.userTaskResumed();
    } else {
      commandWriter.appendFollowUpCommand(
          processInstanceKey, ProcessInstanceIntent.COMPLETE_RESUMING, followUpValue);
    }
  }

  /** Keeps each cycle's writes in their own batch, as {@code RESUME_JOBS} does. */
  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  /**
   * Resumes the first suspended user task found from {@code startAtUserTaskKey}; returns its key,
   * or {@code -1} if none was found.
   */
  private long resumeNextUserTask(final long processInstanceKey, final long startAtUserTaskKey) {
    final AtomicLong nextUserTaskKey = new AtomicLong(-1L);
    final AtomicReference<@Nullable UserTaskRecord> nextUserTask = new AtomicReference<>();
    userTaskState.forEachSuspendedUserTask(
        processInstanceKey,
        startAtUserTaskKey,
        userTaskKey -> {
          // skip an entry whose task is gone, so the chain can't stall on it
          final var userTask = userTaskState.getUserTask(userTaskKey);
          if (userTask == null) {
            return true;
          }
          nextUserTaskKey.set(userTaskKey);
          nextUserTask.set(userTask);
          return false;
        });
    final var userTask = nextUserTask.get();
    if (userTask == null) {
      return -1L;
    }
    // written after the scan, since the RESUMED applier removes the entry it was found by
    final long userTaskKey = nextUserTaskKey.get();
    stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.RESUMED, userTask);
    return userTaskKey;
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionAction.PROCESS;
  }

  private void reject(final TypedRecord<ProcessInstanceRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
