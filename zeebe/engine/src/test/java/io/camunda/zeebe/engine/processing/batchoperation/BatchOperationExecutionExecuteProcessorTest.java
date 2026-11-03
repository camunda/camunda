/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.handlers.BatchOperationExecutor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperationChunk.ChunkItem;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionItem;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers the flow of the item storage ordinals from the chunk to the execution events and the
 * executors, which the engine tests can not cover as the item providers only deliver ordinal 0.
 */
class BatchOperationExecutionExecuteProcessorTest {

  private static final int PARTITION_ID = 1;
  private static final long BATCH_OPERATION_KEY = Protocol.encodePartitionId(PARTITION_ID, 42L);
  private static final long COMMAND_KEY = Protocol.encodePartitionId(PARTITION_ID, 99L);
  private static final int BATCH_OPERATION_ORDINAL = 1234;

  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private BatchOperationState batchOperationState;
  private BatchOperationExecutor executor;
  private PersistedBatchOperation batchOperation;
  private BatchOperationExecutionExecuteProcessor processor;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);
    when(writers.rejection()).thenReturn(mock(TypedRejectionWriter.class));
    when(writers.response()).thenReturn(mock(TypedResponseWriter.class));

    batchOperationState = mock(BatchOperationState.class);
    final var processingState = mock(ProcessingState.class);
    when(processingState.getBatchOperationState()).thenReturn(batchOperationState);

    batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStorageOrdinal(BATCH_OPERATION_ORDINAL)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setStatus(BatchOperationStatus.INITIALIZED);
    when(batchOperationState.get(BATCH_OPERATION_KEY)).thenReturn(Optional.of(batchOperation));

    executor = mock(BatchOperationExecutor.class);

    processor =
        new BatchOperationExecutionExecuteProcessor(
            writers,
            processingState,
            mock(CommandDistributionBehavior.class),
            mock(KeyGenerator.class),
            Map.of(BatchOperationType.CANCEL_PROCESS_INSTANCE, executor),
            mock(BatchOperationMetrics.class));
  }

  @Test
  void shouldWriteExecutionEventsWithEveryItemsOwnOrdinal() {
    // given two items under different ordinals
    when(batchOperationState.getNextItems(eq(BATCH_OPERATION_KEY), anyInt()))
        .thenReturn(List.of(new ChunkItem(1L, 7), new ChunkItem(2L, 8)));

    // when
    processor.processRecord(command());

    // then
    final var intents = ArgumentCaptor.forClass(Intent.class);
    final var values = ArgumentCaptor.forClass(RecordValue.class);
    verify(stateWriter, times(2))
        .appendFollowUpEvent(
            anyLong(), intents.capture(), values.capture(), any(FollowUpEventMetadata.class));

    assertThat(intents.getAllValues())
        .containsExactly(
            BatchOperationExecutionIntent.EXECUTING, BatchOperationExecutionIntent.EXECUTED);
    assertThat(values.getAllValues())
        .hasSize(2)
        .allSatisfy(
            value -> {
              final var record = (BatchOperationExecutionRecord) value;
              assertThat(record.getStorageOrdinal()).isEqualTo(BATCH_OPERATION_ORDINAL);
              assertThat(record.getItems())
                  .containsExactly(
                      new BatchOperationExecutionItem(1L, 7),
                      new BatchOperationExecutionItem(2L, 8));
              assertThat(record.getItemKeys()).containsExactlyInAnyOrder(1L, 2L);
            });

    verify(executor).execute(1L, 7, batchOperation);
    verify(executor).execute(2L, 8, batchOperation);
  }

  @Test
  void shouldWriteEmptyExecutedEventWhenNoItemsLeft() {
    // given
    when(batchOperationState.getNextItems(eq(BATCH_OPERATION_KEY), anyInt())).thenReturn(List.of());

    // when
    processor.processRecord(command());

    // then
    final var intents = ArgumentCaptor.forClass(Intent.class);
    final var values = ArgumentCaptor.forClass(RecordValue.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            anyLong(), intents.capture(), values.capture(), any(FollowUpEventMetadata.class));

    assertThat(intents.getValue()).isEqualTo(BatchOperationExecutionIntent.EXECUTED);
    final var record = (BatchOperationExecutionRecord) values.getValue();
    assertThat(record.getItems()).isEmpty();
    assertThat(record.getItemKeys()).isEmpty();
    assertThat(record.getStorageOrdinal()).isEqualTo(BATCH_OPERATION_ORDINAL);

    verifyNoInteractions(executor);
    verify(commandWriter)
        .appendFollowUpCommand(
            anyLong(), eq(BatchOperationIntent.COMPLETE_PARTITION), any(), any());
  }

  private MockTypedRecord<BatchOperationExecutionRecord> command() {
    return new MockTypedRecord<>(
        COMMAND_KEY,
        new RecordMetadata(),
        new BatchOperationExecutionRecord().setBatchOperationKey(BATCH_OPERATION_KEY));
  }
}
