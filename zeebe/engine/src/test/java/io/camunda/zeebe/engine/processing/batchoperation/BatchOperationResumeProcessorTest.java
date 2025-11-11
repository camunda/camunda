/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.mockito.Mockito.*;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** This test class only covers cases not covered by the LifecycleBatchOperationTest engine test */
class BatchOperationResumeProcessorTest {

  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private BatchOperationResumeProcessor processor;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

    final var batchOperationState = mock(BatchOperationState.class);
    final var state = mock(ProcessingState.class);
    when(state.getBatchOperationState()).thenReturn(batchOperationState);

    // Inject mocked writers
    processor =
        new BatchOperationResumeProcessor(
            writers, null, state, null, null, mock(BatchOperationMetrics.class));
  }

  @Test
  void shouldAppendFollowUpEventWhenResumingBatchOperation() {
    // given
    final long resumeKey = 123L;
    final long batchOperationKey = 456L;
    final BatchOperationLifecycleManagementRecord recordValue =
        new BatchOperationLifecycleManagementRecord();
    recordValue.setBatchOperationKey(batchOperationKey);

    final PersistedBatchOperation batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getKey()).thenReturn(batchOperationKey);
    when(batchOperation.isInitialized()).thenReturn(false);
    final TypedRecord<BatchOperationLifecycleManagementRecord> mockedCommand =
        mockCommand("test-user", recordValue);

    // when
    processor.resumeBatchOperation(resumeKey, batchOperation, mockedCommand);

    // then
    verify(stateWriter)
        .appendFollowUpEvent(
            resumeKey,
            BatchOperationIntent.RESUMED,
            recordValue,
            FollowUpEventMetadata.of(
                m ->
                    m.batchOperationReference(batchOperationKey)
                        .claims(mockedCommand.getAuthorizations())));
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any(), any());
  }

  @Test
  void shouldAppendFollowUpCommandWhenBatchOperationIsInitialized() {
    // given
    final long resumeKey = 123L;
    final long batchOperationKey = 456L;
    final BatchOperationLifecycleManagementRecord recordValue =
        new BatchOperationLifecycleManagementRecord();
    recordValue.setBatchOperationKey(batchOperationKey);

    final PersistedBatchOperation batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getKey()).thenReturn(batchOperationKey);
    when(batchOperation.isInitialized()).thenReturn(true);
    final TypedRecord<BatchOperationLifecycleManagementRecord> mockedCommand =
        mockCommand("test-user", recordValue);

    // when
    processor.resumeBatchOperation(resumeKey, batchOperation, mockedCommand);

    // then
    verify(stateWriter)
        .appendFollowUpEvent(
            resumeKey,
            BatchOperationIntent.RESUMED,
            recordValue,
            FollowUpEventMetadata.of(
                m ->
                    m.batchOperationReference(batchOperationKey)
                        .claims(mockedCommand.getAuthorizations())));

    final ArgumentCaptor<BatchOperationExecutionRecord> captor =
        ArgumentCaptor.forClass(BatchOperationExecutionRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(batchOperationKey),
            eq(BatchOperationExecutionIntent.EXECUTE),
            captor.capture(),
            any());

    final BatchOperationExecutionRecord capturedRecord = captor.getValue();
    assert capturedRecord.getBatchOperationKey() == batchOperationKey;
  }

  private TypedRecord<BatchOperationLifecycleManagementRecord> mockCommand(
      final String username, final BatchOperationLifecycleManagementRecord recordValue) {
    final var command = mock(TypedRecord.class);
    when(command.getAuthorizations()).thenReturn(Map.of(AUTHORIZED_USERNAME, username));
    when(command.hasRequestMetadata()).thenReturn(true);
    when(command.getValue()).thenReturn(recordValue);
    return command;
  }
}
