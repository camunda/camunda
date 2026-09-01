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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class BufferedCommandDrainProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long BUFFERED_COMMAND_KEY = 200L;

  private MutableProcessingState processingState;

  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private SuspensionMetrics suspensionMetrics;
  private BufferedCommandDrainProcessor processor;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    suspensionMetrics = mock(SuspensionMetrics.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

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
  void shouldAppendResumeJobsWhenBufferEmpty() {
    // when
    processor.processRecord(drainCommand());

    // then
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_JOBS),
            any(ProcessInstanceRecord.class));
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  // Note: the peek-after-drain fast path (skip the extra DRAIN when the buffer just emptied) is
  // not unit-testable here — stateWriter is mocked, so appendDrainedEvent's write never actually
  // removes the entry from the real suspensionState this processor reads from. That behavior is
  // covered end-to-end by ResumeProcessInstanceDrainTest instead.

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
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new BufferedCommandRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
  }
}
