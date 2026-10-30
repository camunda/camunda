/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(ProcessingStateExtension.class)
public final class BufferedCommandDrainProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long BUFFERED_COMMAND_KEY = 200L;
  private static final long SUBSCRIPTION_KEY = 300L;
  private static final int STORAGE_ORDINAL = 1234;

  private MutableProcessingState processingState;

  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private TypedRejectionWriter rejectionWriter;
  private SuspensionMetrics suspensionMetrics;
  private BufferedCommandDrainProcessor processor;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    rejectionWriter = mock(TypedRejectionWriter.class);
    suspensionMetrics = mock(SuspensionMetrics.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);

    processor = new BufferedCommandDrainProcessor(processingState, writers, suspensionMetrics);
  }

  @Test
  void shouldHaltWithoutWritesOnDrainErrorWhenBufferHasEntries() {
    // given
    bufferCompleteElementCommand();

    // when
    final var result = processor.tryHandleError(drainCommand(), new RuntimeException("boom"));

    // then - state-free: does not re-read the buffer, drop the command, or continue the chain;
    // the engine's default UNEXPECTED_ERROR handling rejects the DRAIN and halts the instance
    assertThat(result).isEqualTo(ProcessingError.UNEXPECTED_ERROR);
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldHaltWithoutWritesOnDrainErrorWhenBufferEmpty() {
    // when
    final var result = processor.tryHandleError(drainCommand(), new RuntimeException("boom"));

    // then - same outcome regardless of buffer contents, confirming the handler never queries it
    assertThat(result).isEqualTo(ProcessingError.UNEXPECTED_ERROR);
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldAppendResumeJobsWhenBufferEmptyAndNoUserTaskSuspended() {
    // when
    processor.processRecord(drainCommand());

    // then
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_JOBS),
            any(ProcessInstanceRecord.class));
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.RESUME_USER_TASKS), any());
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  @Test
  void shouldAppendResumeUserTasksWhenBufferEmptyAndUserTaskSuspended() {
    // given - user tasks resume before jobs
    processingState.getUserTaskState().markSuspended(PROCESS_INSTANCE_KEY, 300L);

    // when
    processor.processRecord(drainCommand());

    // then
    final var followUp = ArgumentCaptor.forClass(ProcessInstanceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_USER_TASKS),
            followUp.capture());
    assertThat(followUp.getValue().getResumeFromUserTaskKey()).isEqualTo(-1L);
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.RESUME_JOBS), any());
  }

  @Test
  void shouldDrainAndAppendNextDrainWhenBufferHasEntries() {
    // given
    bufferCompleteElementCommand();

    // when
    processor.processRecord(drainCommand());

    // then - the buffered command is replayed and marked drained, and another DRAIN is scheduled
    // unconditionally rather than checking whether the buffer is now empty
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(BUFFERED_COMMAND_KEY),
            eq(ProcessInstanceIntent.COMPLETE_ELEMENT),
            any(ProcessInstanceRecord.class));
    verify(stateWriter)
        .appendFollowUpEvent(eq(BUFFERED_COMMAND_KEY), eq(BufferedCommandIntent.DRAINED), any());

    final var nextDrainValue = ArgumentCaptor.forClass(BufferedCommandRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY), eq(BufferedCommandIntent.DRAIN), nextDrainValue.capture());
    assertThat(nextDrainValue.getValue().getCommandKey()).isEqualTo(BUFFERED_COMMAND_KEY);

    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.RESUME_JOBS), any());
  }

  @Test
  void shouldStampOrdinalFromInstanceWhenDrainCarriesNone() {
    // given
    processingState
        .getElementInstanceState()
        .newInstance(
            PROCESS_INSTANCE_KEY,
            new ProcessInstanceRecord()
                .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
                .setStorageOrdinal(STORAGE_ORDINAL),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    bufferCompleteElementCommand();

    // when
    processor.processRecord(drainCommand());

    // then - instance in state is the source, DRAIN value carries no ordinal
    final var drainedValue = ArgumentCaptor.forClass(BufferedCommandRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(BUFFERED_COMMAND_KEY), eq(BufferedCommandIntent.DRAINED), drainedValue.capture());
    assertThat(drainedValue.getValue().getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);

    final var nextDrainValue = ArgumentCaptor.forClass(BufferedCommandRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY), eq(BufferedCommandIntent.DRAIN), nextDrainValue.capture());
    assertThat(nextDrainValue.getValue().getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldFallBackToDrainOrdinalWhenInstanceIsGone() {
    // given - no element instance in state, buffer empty

    // when
    processor.processRecord(drainCommand(STORAGE_ORDINAL));

    // then - DRAIN value fallback is used since the instance is gone
    final var resumeJobsValue = ArgumentCaptor.forClass(ProcessInstanceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_JOBS),
            resumeJobsValue.capture());
    assertThat(resumeJobsValue.getValue().getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);
  }

  @Test
  void shouldRejectDrainWithInstanceOrdinalWhileSubscriptionsAreClosing() {
    // given an active instance whose message subscription is still CLOSING, and an empty buffer
    processingState
        .getElementInstanceState()
        .newInstance(
            PROCESS_INSTANCE_KEY,
            new ProcessInstanceRecord()
                .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
                .setStorageOrdinal(STORAGE_ORDINAL),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var subscription =
        new ProcessMessageSubscriptionRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setElementInstanceKey(PROCESS_INSTANCE_KEY)
            .setProcessDefinitionKey(1L)
            .setBpmnProcessId(wrapString("process"))
            .setElementId(wrapString("catch"))
            .setMessageName(wrapString("message"))
            .setCorrelationKey(wrapString("key"))
            .setInterrupting(true)
            .setSubscriptionPartitionId(1);
    final var subscriptionState = processingState.getProcessMessageSubscriptionState();
    subscriptionState.put(SUBSCRIPTION_KEY, subscription);
    subscriptionState.updateToClosingState(subscription);
    final var drain = drainCommand();

    // when
    processor.processRecord(drain);

    // then - the rejection waits for the close acks and carries the instance's ordinal from state
    verify(rejectionWriter)
        .appendRejection(same(drain), eq(RejectionType.INVALID_STATE), anyString());
    assertThat(drain.getValue().getStorageOrdinal()).isEqualTo(STORAGE_ORDINAL);
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.RESUME_JOBS), any());
  }

  private void bufferCompleteElementCommand() {
    final var command =
        new BufferedCommandRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setCommandKey(BUFFERED_COMMAND_KEY)
            .setValueType(ValueType.PROCESS_INSTANCE)
            .setIntent(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .setCommandValue(
                new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    processingState.getSuspensionState().bufferCommand(BUFFERED_COMMAND_KEY, command);
  }

  private MockTypedRecord<BufferedCommandRecord> drainCommand() {
    return drainCommand(0);
  }

  private MockTypedRecord<BufferedCommandRecord> drainCommand(final int storageOrdinal) {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new BufferedCommandRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setStorageOrdinal(storageOrdinal));
  }
}
