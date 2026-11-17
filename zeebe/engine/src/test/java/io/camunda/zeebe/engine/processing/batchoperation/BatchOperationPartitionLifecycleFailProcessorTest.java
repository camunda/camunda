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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Covers the storage ordinal of the internal fail command on the lead partition. */
class BatchOperationPartitionLifecycleFailProcessorTest {

  private static final int PARTITION_ID = 1;
  private static final long BATCH_OPERATION_KEY = Protocol.encodePartitionId(PARTITION_ID, 42L);
  private static final long COMMAND_KEY = Protocol.encodePartitionId(PARTITION_ID, 99L);
  private static final int COMMAND_ORDINAL = 1234;
  private static final int PERSISTED_ORDINAL = 4321;

  private TypedCommandWriter commandWriter;
  private BatchOperationState batchOperationState;
  private BatchOperationPartitionLifecycleFailProcessor processor;

  @BeforeEach
  void setUp() {
    commandWriter = mock(TypedCommandWriter.class);
    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(mock(StateWriter.class));
    when(writers.command()).thenReturn(commandWriter);
    batchOperationState = mock(BatchOperationState.class);
    final var processingState = mock(ProcessingState.class);
    when(processingState.getBatchOperationState()).thenReturn(batchOperationState);
    processor =
        new BatchOperationPartitionLifecycleFailProcessor(
            writers,
            mock(CommandDistributionBehavior.class),
            mock(KeyGenerator.class),
            mock(BatchOperationMetrics.class),
            processingState);
  }

  @Test
  void shouldStampInternalFailFromState() {
    // given
    when(batchOperationState.get(BATCH_OPERATION_KEY))
        .thenReturn(
            Optional.of(
                new PersistedBatchOperation()
                    .setKey(BATCH_OPERATION_KEY)
                    .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
                    .setStorageOrdinal(PERSISTED_ORDINAL)));

    // when
    processor.processRecord(failCommand());

    // then
    assertThat(writtenFailPartition().getStorageOrdinal()).isEqualTo(PERSISTED_ORDINAL);
  }

  @Test
  void shouldKeepCommandOrdinalWhenBatchOperationIsGone() {
    // given a scheduler FAIL that arrives after the batch operation left state
    when(batchOperationState.get(BATCH_OPERATION_KEY)).thenReturn(Optional.empty());

    // when
    processor.processRecord(failCommand());

    // then
    assertThat(writtenFailPartition().getStorageOrdinal()).isEqualTo(COMMAND_ORDINAL);
  }

  private BatchOperationPartitionLifecycleRecord writtenFailPartition() {
    final var captor = ArgumentCaptor.forClass(RecordValue.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationIntent.FAIL_PARTITION),
            captor.capture(),
            any());
    return (BatchOperationPartitionLifecycleRecord) captor.getValue();
  }

  private static MockTypedRecord<BatchOperationPartitionLifecycleRecord> failCommand() {
    return new MockTypedRecord<>(
        COMMAND_KEY,
        new RecordMetadata(),
        new BatchOperationPartitionLifecycleRecord()
            .setBatchOperationKey(BATCH_OPERATION_KEY)
            .setStorageOrdinal(COMMAND_ORDINAL));
  }
}
