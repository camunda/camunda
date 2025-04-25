/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.batchoperation.handlers.BatchOperationExecutor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationExecuteProcessor
    implements TypedRecordProcessor<BatchOperationExecutionRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationExecuteProcessor.class);

  private static final int BATCH_SIZE = 10;

  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final int partitionId;
  private final BatchOperationState batchOperationState;

  private final Map<BatchOperationType, BatchOperationExecutor> handlers;

  public BatchOperationExecuteProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final int partitionId,
      final Map<BatchOperationType, BatchOperationExecutor> handlers) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    batchOperationState = processingState.getBatchOperationState();
    this.partitionId = partitionId;
    this.handlers = handlers;
  }

  @Override
  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  public void processRecord(final TypedRecord<BatchOperationExecutionRecord> command) {
    final var executionRecord = command.getValue();
    LOGGER.debug(
        "Processing new command with key '{}' on partition{} : {}",
        command.getKey(),
        partitionId,
        executionRecord);
    final long batchKey = executionRecord.getBatchOperationKey();

    final var batchOperation = getBatchOperation(batchKey);
    if (batchOperation == null) {
      LOGGER.debug("No batch operation found for key '{}'.", batchKey);
      return;
    }

    if (batchOperation.isPaused()) {
      LOGGER.info("Batch operation {} is paused.", batchOperation.getKey());
      return;
    }

    final var entityKeys = batchOperationState.getNextItemKeys(batchKey, BATCH_SIZE);
    if (entityKeys.isEmpty()) {
      LOGGER.debug(
          "No items to process for BatchOperation {} on partition {}", batchKey, partitionId);

      appendBatchOperationExecutionExecutedEvent(command.getValue(), Collections.emptySet());
      appendBatchOperationExecutionCompletedEvent(command.getValue());
      return;
    }

    appendBatchOperationExecutionExecutingEvent(command.getValue(), Set.copyOf(entityKeys));

    final var handler = handlers.get(batchOperation.getBatchOperationType());
    entityKeys.forEach(entityKey -> handler.execute(entityKey, batchOperation));

    appendBatchOperationExecutionExecutedEvent(command.getValue(), Set.copyOf(entityKeys));

    LOGGER.debug(
        "Scheduling next batch for BatchOperation {} on partition {}", batchKey, partitionId);
    final var followupCommand = new BatchOperationExecutionRecord();
    followupCommand.setBatchOperationKey(batchKey);
    commandWriter.appendFollowUpCommand(
        command.getKey(), BatchOperationExecutionIntent.EXECUTE, followupCommand, batchKey);
  }

  private PersistedBatchOperation getBatchOperation(final long batchOperationKey) {
    return batchOperationState.get(batchOperationKey).orElse(null);
  }

  private void appendBatchOperationExecutionExecutingEvent(
      final BatchOperationExecutionRecord executionRecord, final Set<Long> keys) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(executionRecord.getBatchOperationKey());
    batchExecute.setItemKeys(keys);
    stateWriter.appendFollowUpEvent(
        executionRecord.getBatchOperationKey(),
        BatchOperationExecutionIntent.EXECUTING,
        batchExecute);
  }

  private void appendBatchOperationExecutionExecutedEvent(
      final BatchOperationExecutionRecord executionRecord, final Set<Long> keys) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(executionRecord.getBatchOperationKey());
    batchExecute.setItemKeys(keys);
    stateWriter.appendFollowUpEvent(
        executionRecord.getBatchOperationKey(),
        BatchOperationExecutionIntent.EXECUTED,
        batchExecute);
  }

  private void appendBatchOperationExecutionCompletedEvent(
      final BatchOperationExecutionRecord executionRecord) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(executionRecord.getBatchOperationKey());
    stateWriter.appendFollowUpEvent(
        executionRecord.getBatchOperationKey(),
        BatchOperationExecutionIntent.COMPLETED,
        batchExecute);
  }
}
