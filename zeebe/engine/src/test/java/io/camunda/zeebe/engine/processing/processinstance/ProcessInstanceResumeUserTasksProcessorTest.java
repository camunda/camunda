/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

/**
 * Uses real element instance, user task and suspension state, mocking only the writers, like {@code
 * ProcessInstanceResumeJobsProcessorTest} does for the job step of the same resume chain.
 */
@ExtendWith(ProcessingStateExtension.class)
public final class ProcessInstanceResumeUserTasksProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long USER_TASK_KEY = 300L;
  private static final long OTHER_USER_TASK_KEY = 301L;

  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableUserTaskState userTaskState;
  private MutableSuspensionState suspensionState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private TypedRejectionWriter rejectionWriter;
  private ProcessInstanceResumeUserTasksProcessor processor;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    userTaskState = processingState.getUserTaskState();
    suspensionState = processingState.getSuspensionState();
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    rejectionWriter = mock(TypedRejectionWriter.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);

    processor =
        new ProcessInstanceResumeUserTasksProcessor(
            processingState, writers, mock(SuspensionMetrics.class));

    suspensionState.setSuspensionState(PROCESS_INSTANCE_KEY, SuspensionState.State.RESUMING);
  }

  @Test
  void shouldAppendCompleteResumingWhenNoSuspendedUserTasksRemain() {
    // given
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    processor.processRecord(resumeUserTasksCommand(-1L));

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.COMPLETE_RESUMING),
            any(ProcessInstanceRecord.class));
    verify(rejectionWriter, never()).appendRejection(any(), any(), any());
  }

  @Test
  void shouldRequestSeparateBatchForEachCycle() {
    // when / then
    assertThat(processor.shouldProcessResultsInSeparateBatches()).isTrue();
  }

  @Test
  void shouldResumeOneUserTaskAndCarryItsKeyAsCursor() {
    // given - two suspended user tasks
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    suspendUserTask(USER_TASK_KEY);
    suspendUserTask(OTHER_USER_TASK_KEY);

    // when
    processor.processRecord(resumeUserTasksCommand(-1L));

    // then - only the first is resumed, and the next cycle continues from it
    verify(stateWriter, times(1)).appendFollowUpEvent(anyLong(), eq(UserTaskIntent.RESUMED), any());
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(USER_TASK_KEY), eq(UserTaskIntent.RESUMED), any(UserTaskRecord.class));
    final var followUp = ArgumentCaptor.forClass(ProcessInstanceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_USER_TASKS),
            followUp.capture());
    assertThat(followUp.getValue().getResumeFromUserTaskKey()).isEqualTo(USER_TASK_KEY);
  }

  @Test
  void shouldResumeFromTheCursorCarriedOnTheCommand() {
    // given - the first task was resumed by an earlier cycle, but its entry is still present
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    suspendUserTask(USER_TASK_KEY);
    suspendUserTask(OTHER_USER_TASK_KEY);

    // when
    processor.processRecord(resumeUserTasksCommand(OTHER_USER_TASK_KEY));

    // then
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(OTHER_USER_TASK_KEY), eq(UserTaskIntent.RESUMED), any(UserTaskRecord.class));
    verify(stateWriter, never()).appendFollowUpEvent(eq(USER_TASK_KEY), any(), any());
  }

  @Test
  void shouldSkipTrackedUserTaskThatNoLongerExists() {
    // given - a stale entry whose user task is gone, before a task that still exists
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    userTaskState.markSuspended(PROCESS_INSTANCE_KEY, USER_TASK_KEY);
    suspendUserTask(OTHER_USER_TASK_KEY);

    // when
    processor.processRecord(resumeUserTasksCommand(-1L));

    // then - the existing task is resumed and the chain moves past the stale entry
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(OTHER_USER_TASK_KEY), eq(UserTaskIntent.RESUMED), any(UserTaskRecord.class));
    verify(stateWriter, never()).appendFollowUpEvent(eq(USER_TASK_KEY), any(), any());
    final var followUp = ArgumentCaptor.forClass(ProcessInstanceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_USER_TASKS),
            followUp.capture());
    assertThat(followUp.getValue().getResumeFromUserTaskKey()).isEqualTo(OTHER_USER_TASK_KEY);
  }

  @Test
  void shouldAppendCompleteResumingWhenOnlyStaleEntriesRemain() {
    // given
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    userTaskState.markSuspended(PROCESS_INSTANCE_KEY, USER_TASK_KEY);

    // when
    processor.processRecord(resumeUserTasksCommand(-1L));

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.COMPLETE_RESUMING),
            any(ProcessInstanceRecord.class));
  }

  @Test
  void shouldRejectWhenInstanceCanceledMidResume() {
    // given - no element instance, so the instance is gone

    // when
    processor.processRecord(resumeUserTasksCommand(-1L));

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    final var reason = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reason.capture());
    assertThat(reason.getValue()).contains("no longer exists");
  }

  @Test
  void shouldRejectWhenSuspensionMarkerNoLongerResuming() {
    // given
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    suspendUserTask(USER_TASK_KEY);
    suspensionState.setSuspensionState(PROCESS_INSTANCE_KEY, SuspensionState.State.SUSPENDED);

    // when
    processor.processRecord(resumeUserTasksCommand(-1L));

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    final var reason = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reason.capture());
    assertThat(reason.getValue()).contains("no longer RESUMING");
  }

  @Test
  void shouldRejectWhenInstanceIsTerminating() {
    // given
    rootInstance(ProcessInstanceIntent.ELEMENT_TERMINATING);
    suspendUserTask(USER_TASK_KEY);

    // when
    processor.processRecord(resumeUserTasksCommand(-1L));

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    final var reason = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reason.capture());
    assertThat(reason.getValue()).contains(ProcessInstanceIntent.ELEMENT_TERMINATING.name());
  }

  private void rootInstance(final ProcessInstanceIntent state) {
    elementInstanceState.newInstance(
        PROCESS_INSTANCE_KEY,
        new ProcessInstanceRecord()
            .setElementId("process")
            .setBpmnProcessId("process")
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setFlowScopeKey(-1L)
            .setVersion(1)
            .setProcessDefinitionKey(1L)
            .setBpmnElementType(BpmnElementType.PROCESS),
        state);
  }

  /** Creates a user task and tracks it as suspended, as the SUSPENDED applier would. */
  private void suspendUserTask(final long userTaskKey) {
    userTaskState.create(
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setElementInstanceKey(PROCESS_INSTANCE_KEY)
            .setBpmnProcessId("process")
            .setElementId("task"));
    userTaskState.markSuspended(PROCESS_INSTANCE_KEY, userTaskKey);
  }

  private MockTypedRecord<ProcessInstanceRecord> resumeUserTasksCommand(final long cursor) {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setResumeFromUserTaskKey(cursor));
  }
}
