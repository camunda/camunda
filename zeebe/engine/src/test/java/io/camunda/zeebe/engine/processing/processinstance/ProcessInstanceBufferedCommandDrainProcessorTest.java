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

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class ProcessInstanceBufferedCommandDrainProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long BUFFERED_COMMAND_KEY = 200L;

  private MutableProcessingState processingState;

  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private ProcessInstanceBufferedCommandDrainProcessor processor;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

    processor = new ProcessInstanceBufferedCommandDrainProcessor(processingState, writers);
  }

  @Test
  void shouldDropCommandAndContinueOnDrainError() {
    // given
    bufferCompleteElementCommand();

    // when
    final var result = processor.tryHandleError(drainCommand(), new RuntimeException("boom"));

    // then
    assertThat(result).isEqualTo(ProcessingError.EXPECTED_ERROR);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(BUFFERED_COMMAND_KEY),
            eq(ProcessInstanceBufferedCommandIntent.DRAINED),
            any(ProcessInstanceBufferedCommandRecord.class));
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceBufferedCommandIntent.DRAIN),
            any(ProcessInstanceBufferedCommandRecord.class));
  }

  @Test
  void shouldFallBackToUnexpectedErrorWhenNothingBuffered() {
    // when
    final var result = processor.tryHandleError(drainCommand(), new RuntimeException("boom"));

    // then
    assertThat(result).isEqualTo(ProcessingError.UNEXPECTED_ERROR);
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldNotWriteResumedWhenInstanceNoLongerExists() {
    // when
    processor.processRecord(drainCommand());

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  private void bufferCompleteElementCommand() {
    final var command =
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setCommandKey(BUFFERED_COMMAND_KEY)
            .setValueType(ValueType.PROCESS_INSTANCE)
            .setIntent(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .setCommandValue(
                new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
    processingState.getSuspensionState().bufferCommand(BUFFERED_COMMAND_KEY, command);
  }

  private MockTypedRecord<ProcessInstanceBufferedCommandRecord> drainCommand() {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new ProcessInstanceBufferedCommandRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
  }
}
