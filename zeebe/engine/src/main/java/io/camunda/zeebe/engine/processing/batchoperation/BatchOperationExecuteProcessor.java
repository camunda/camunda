/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
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

  public BatchOperationExecuteProcessor(
      final Writers writers, final ProcessingState processingState, final int partitionId) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    batchOperationState = processingState.getBatchOperationState();
    this.partitionId = partitionId;
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationExecutionRecord> command) {
    final var recordValue = command.getValue();
    LOGGER.debug(
        "Processing new command with key '{}' on partition{} : {}",
        command.getKey(),
        partitionId,
        recordValue);
    final long batchKey = command.getValue().getBatchOperationKey();
    final int offset = command.getValue().getOffset();

    final var batchOperation = getBatchOperation(batchKey);
    if (batchOperation == null) {
      LOGGER.debug("No batch operation found for key '{}'. Probably has been canceled", batchKey);
      return;
    }

    if (batchOperation.getStatus() == PersistedBatchOperation.BatchOperationState.PAUSED) {
      LOGGER.debug("Batch operation with key '{}' has been paused, Doing nothing", batchKey);
      return;
    }

    final var entityKeys = batchOperationState.getNextEntityKeys(batchKey, BATCH_SIZE);
    if (entityKeys.isEmpty()) {
      LOGGER.debug(
          "No items to process for BatchOperation {} on partition {}", batchKey, partitionId);
      appendBatchOperationExecutionCompletedEvent(offset, command);
      return;
    }

    appendBatchOperationExecutionExecutingEvent(command, Set.copyOf(entityKeys));

    switch (recordValue.getBatchOperationType()) {
      case PROCESS_CANCELLATION ->
          entityKeys.forEach(entityKey -> cancelProcessInstance(entityKey, batchKey));
    }

    final var newOffset = offset + entityKeys.size();
    LOGGER.debug(
        "Scheduling next batch for BatchOperation {} on partition {} with offset {}",
        batchKey,
        partitionId,
        newOffset);
    final var followupCommand = new BatchOperationExecutionRecord();
    followupCommand.setBatchOperationKey(batchKey);
    followupCommand.setBatchOperationType(command.getValue().getBatchOperationType());
    followupCommand.setOffset(newOffset);
    commandWriter.appendFollowUpCommand(
        command.getKey(), BatchOperationIntent.EXECUTE, followupCommand, batchKey);

    appendBatchOperationExecutionExecutedEvent(command, Set.copyOf(entityKeys));
  }

  private PersistedBatchOperation getBatchOperation(final long batchOperationKey) {
    return batchOperationState.get(batchOperationKey).orElse(null);
  }

  private void cancelProcessInstance(final long processInstanceKey, final long batchKey) {
    LOGGER.info("Cancelling process instance with key '{}'", processInstanceKey);

    final var command = new ProcessInstanceRecord();
    command.setProcessInstanceKey(processInstanceKey);
    commandWriter.appendFollowUpCommand(
        processInstanceKey, ProcessInstanceIntent.CANCEL, command, batchKey);
  }

  private void appendBatchOperationExecutionExecutingEvent(
      final TypedRecord<BatchOperationExecutionRecord> command, final Set<Long> keys) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(command.getValue().getBatchOperationKey());
    batchExecute.setKeys(keys);
    batchExecute.setBatchOperationType(command.getValue().getBatchOperationType());
    batchExecute.setOffset(command.getValue().getOffset());
    stateWriter.appendFollowUpEvent(command.getValue().getBatchOperationKey(),
        BatchOperationIntent.EXECUTING, batchExecute);
  }

  private void appendBatchOperationExecutionExecutedEvent(
      final TypedRecord<BatchOperationExecutionRecord> command, final Set<Long> keys) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(command.getValue().getBatchOperationKey());
    batchExecute.setKeys(keys);
    batchExecute.setBatchOperationType(command.getValue().getBatchOperationType());
    batchExecute.setOffset(command.getValue().getOffset());
    stateWriter.appendFollowUpEvent(command.getKey(), BatchOperationIntent.EXECUTED, batchExecute);
  }

  private void appendBatchOperationExecutionCompletedEvent(
      final int offset, final TypedRecord<BatchOperationExecutionRecord> command) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(command.getValue().getBatchOperationKey());
    batchExecute.setBatchOperationType(command.getValue().getBatchOperationType());
    batchExecute.setOffset(offset);
    stateWriter.appendFollowUpEvent(command.getKey(), BatchOperationIntent.COMPLETED, batchExecute);
  }
}
