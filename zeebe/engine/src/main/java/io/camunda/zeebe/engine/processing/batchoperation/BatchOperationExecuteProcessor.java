/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.batchoperation.handlers.BatchOperationExecutor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
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
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final int partitionId;
  private final BatchOperationState batchOperationState;
  private final KeyGenerator keyGenerator;
  private final BatchOperationMetrics metrics;

  private final Map<BatchOperationType, BatchOperationExecutor> handlers;

  public BatchOperationExecuteProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final KeyGenerator keyGenerator,
      final int partitionId,
      final Map<BatchOperationType, BatchOperationExecutor> handlers,
      final BatchOperationMetrics metrics) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    batchOperationState = processingState.getBatchOperationState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.keyGenerator = keyGenerator;
    this.partitionId = partitionId;
    this.handlers = handlers;
    this.metrics = metrics;
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

    // Stop the measure for the batch operation execute cycle latency which was started the last
    // time
    metrics.stopExecuteCycleLatencyMeasure(batchKey);

    final var batchOperation = getBatchOperation(batchKey);
    if (batchOperation == null) {
      LOGGER.debug("No batch operation found for key '{}'.", batchKey);
      return;
    }

    if (batchOperation.isSuspended()) {
      LOGGER.info("Batch operation {} is suspended.", batchOperation.getKey());
      return;
    }

    final var entityKeys = batchOperationState.getNextItemKeys(batchKey, BATCH_SIZE);
    if (entityKeys.isEmpty()) {
      LOGGER.debug(
          "No items to process for BatchOperation {} on partition {}", batchKey, partitionId);

      appendBatchOperationExecutionExecutedEvent(command.getValue(), Collections.emptySet());
      appendBatchOperationExecutionCompletedEvent(command.getValue());

      metrics.stopTotalExecutionLatencyMeasure(batchKey);
      return;
    }

    // This is only done for the first batch operation execution iteration
    metrics.stopStartExecuteLatencyMeasure(batchKey);

    appendBatchOperationExecutionExecutingEvent(command.getValue(), Set.copyOf(entityKeys));

    final var handler = handlers.get(batchOperation.getBatchOperationType());
    entityKeys.forEach(entityKey -> handler.execute(entityKey, batchOperation));

    appendBatchOperationExecutionExecutedEvent(command.getValue(), Set.copyOf(entityKeys));
    appendBatchOperationExecuteCommand(command, batchKey, batchOperation);

    metrics.startExecuteCycleLatencyMeasure(batchKey, batchOperation.getBatchOperationType());
  }

  private PersistedBatchOperation getBatchOperation(final long batchOperationKey) {
    return batchOperationState.get(batchOperationKey).orElse(null);
  }

  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  private void appendBatchOperationExecuteCommand(
      final TypedRecord<BatchOperationExecutionRecord> command,
      final long batchKey,
      final PersistedBatchOperation batchOperation) {
    LOGGER.debug(
        "Scheduling next batch for BatchOperation {} on partition {}", batchKey, partitionId);
    final var followupCommand = new BatchOperationExecutionRecord();
    followupCommand.setBatchOperationKey(batchKey);
    commandWriter.appendFollowUpCommand(
        command.getKey(),
        BatchOperationExecutionIntent.EXECUTE,
        followupCommand,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperation.getKey())));

    metrics.recordExecuted(batchOperation.getBatchOperationType());
  }

  private void appendBatchOperationExecutionExecutingEvent(
      final BatchOperationExecutionRecord executionRecord, final Set<Long> keys) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(executionRecord.getBatchOperationKey());
    batchExecute.setItemKeys(keys);
    stateWriter.appendFollowUpEvent(
        executionRecord.getBatchOperationKey(),
        BatchOperationExecutionIntent.EXECUTING,
        batchExecute,
        FollowUpEventMetadata.of(
            b -> b.batchOperationReference(executionRecord.getBatchOperationKey())));
  }

  private void appendBatchOperationExecutionExecutedEvent(
      final BatchOperationExecutionRecord executionRecord, final Set<Long> keys) {
    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(executionRecord.getBatchOperationKey());
    batchExecute.setItemKeys(keys);
    stateWriter.appendFollowUpEvent(
        executionRecord.getBatchOperationKey(),
        BatchOperationExecutionIntent.EXECUTED,
        batchExecute,
        FollowUpEventMetadata.of(
            b -> b.batchOperationReference(executionRecord.getBatchOperationKey())));
  }

  private void appendBatchOperationExecutionCompletedEvent(
      final BatchOperationExecutionRecord executionRecord) {

    final int originPartitionId =
        Protocol.decodePartitionId(executionRecord.getBatchOperationKey());
    final var batchInternalComplete =
        new BatchOperationPartitionLifecycleRecord()
            .setBatchOperationKey(executionRecord.getBatchOperationKey())
            .setSourcePartitionId(partitionId);

    LOGGER.debug(
        "Send internal complete command for batch operation {} to original partition {}",
        executionRecord.getBatchOperationKey(),
        originPartitionId);

    if (originPartitionId == partitionId) {
      commandWriter.appendFollowUpCommand(
          executionRecord.getBatchOperationKey(),
          BatchOperationIntent.COMPLETE_PARTITION,
          batchInternalComplete,
          FollowUpCommandMetadata.of(
              b -> b.batchOperationReference(executionRecord.getBatchOperationKey())));
    } else {
      stateWriter.appendFollowUpEvent(
          executionRecord.getBatchOperationKey(),
          BatchOperationIntent.PARTITION_COMPLETED,
          batchInternalComplete,
          FollowUpEventMetadata.of(
              b -> b.batchOperationReference(executionRecord.getBatchOperationKey())));
      commandDistributionBehavior
          .withKey(keyGenerator.nextKey())
          .inQueue(DistributionQueue.BATCH_OPERATION)
          .forPartition(originPartitionId)
          .distribute(
              ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
              BatchOperationIntent.COMPLETE_PARTITION,
              batchInternalComplete);
    }
  }
}
