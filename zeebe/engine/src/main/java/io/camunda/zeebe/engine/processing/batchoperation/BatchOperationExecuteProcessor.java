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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationRelated;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main execution processor for batch operations. When an EXECUTE command is processed,
 * the following steps will be executed:
 *
 * <ul>
 *   <li>Check if the batch operation exists and is not suspended. Otherwise, the processor will
 *       skip further execution.
 *   <li>Retrieve the next batch of item keys to process (up to <code>BATCH_SIZE</code>).
 *   <li>If there are no item keys to process:
 *       <ul>
 *         <li>Mark this partition as completed.
 *         <li>Distribute a <code>COMPLETE_PARTITION</code> command to the lead partition.
 *       </ul>
 *   <li>If there are item keys to process:
 *       <ul>
 *         <li>Execute the batch operation for each item key using the appropriate handler for the
 *             type of the batch operation.
 *       </ul>
 *   <li>Append an <code>EXECUTING</code> event to the state writer, followed by an <code>EXECUTED
 *       </code> event.
 *   <li>Append the next follow-up <code>EXECUTE</code> command to process the next batch of item
 *       keys, if any are left. This creates an <code>EXECUTE</code>-loop until all items are
 *       processed.
 * </ul>
 */
@ExcludeAuthorizationCheck
public final class BatchOperationExecuteProcessor
    implements TypedRecordProcessor<BatchOperationExecutionRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationExecuteProcessor.class);

  private static final int BATCH_SIZE = 10;
  private static final int HEARTBEAT_INTERVAL = 1000;

  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final BatchOperationState batchOperationState;
  private final KeyGenerator keyGenerator;
  private final BatchOperationMetrics metrics;

  private final Map<BatchOperationType, BatchOperationExecutor> handlers;

  public BatchOperationExecuteProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final KeyGenerator keyGenerator,
      final Map<BatchOperationType, BatchOperationExecutor> handlers,
      final BatchOperationMetrics metrics) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    batchOperationState = processingState.getBatchOperationState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.keyGenerator = keyGenerator;
    this.handlers = handlers;
    this.metrics = metrics;
  }

  @Override
  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  public void processRecord(final TypedRecord<BatchOperationExecutionRecord> command) {
    final var executionRecord = command.getValue();
    final long batchKey = executionRecord.getBatchOperationKey();
    LOGGER.trace(
        "Executing next items of batch operation {} on partition {}",
        batchKey,
        command.getPartitionId());

    // Stop the measure for the batch operation execute cycle latency which was started the last
    // time
    metrics.stopExecuteCycleLatencyMeasure(batchKey);

    final var batchOperation = getBatchOperation(batchKey);
    if (batchOperation == null) {
      final var message = "No batch operation found for key '%d'.".formatted(batchKey);
      LOGGER.debug(message);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, message);
      return;
    }

    // if suspended, skip execution and stop the EXECUTE-loop
    if (batchOperation.isSuspended()) {
      final var message = "Batch operation `%d` is suspended.".formatted(batchKey);
      LOGGER.info(message);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, message);
      return;
    }

    final var entityKeys = batchOperationState.getNextItemKeys(batchKey, BATCH_SIZE);
    // If there are no more items to process, we can mark the partition as completed
    if (entityKeys.isEmpty()) {
      LOGGER.debug(
          "No items to process for BatchOperation {} on partition {}",
          batchKey,
          command.getPartitionId());

      appendBatchOperationExecutionExecutedEvent(batchOperation, Collections.emptySet());
      appendBatchOperationExecutionCompletedEvent(command);

      metrics.stopTotalExecutionLatencyMeasure(batchKey);
      return;
    }

    // This is only done for the first batch operation execution iteration
    metrics.stopStartExecuteLatencyMeasure(batchKey);

    // mark the items as in progress
    appendBatchOperationExecutionExecutingEvent(command.getValue(), Set.copyOf(entityKeys));

    // retrieve the handler for the batch operation type and execute each itemKey with it
    final var handler = handlers.get(batchOperation.getBatchOperationType());
    entityKeys.forEach(entityKey -> handler.execute(entityKey, batchOperation));

    // schedule the next EXECUTE command to continue processing the next batch of items
    appendBatchOperationExecutionExecutedEvent(batchOperation, Set.copyOf(entityKeys));
    appendBatchOperationExecuteCommand(command, batchKey, batchOperation);

    metrics.startExecuteCycleLatencyMeasure(batchKey, batchOperation.getBatchOperationType());
  }

  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  private PersistedBatchOperation getBatchOperation(final long batchOperationKey) {
    return batchOperationState.get(batchOperationKey).orElse(null);
  }

  private void appendBatchOperationExecuteCommand(
      final TypedRecord<BatchOperationExecutionRecord> command,
      final long batchKey,
      final PersistedBatchOperation batchOperation) {
    LOGGER.trace(
        "Scheduling next batch for BatchOperation {} on partition {}",
        batchKey,
        command.getPartitionId());
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
      final PersistedBatchOperation batchOperation, final Set<Long> keys) {
    final var batchExecute = new BatchOperationExecutionRecord();

    // Occasionally log a heartbeat
    // This modulo only works good if batch size is a divider of HEARTBEAT_INTERVAL
    if (batchOperation.getNumExecutedItems() % HEARTBEAT_INTERVAL == 0) {
      LOGGER.debug(
          "Batch operation {} on has executed {} of {} items.",
          batchOperation.getKey(),
          batchOperation.getNumExecutedItems(),
          batchOperation.getNumTotalItems());
    }

    batchExecute.setBatchOperationKey(batchOperation.getKey());
    batchExecute.setItemKeys(keys);
    stateWriter.appendFollowUpEvent(
        batchOperation.getKey(),
        BatchOperationExecutionIntent.EXECUTED,
        batchExecute,
        FollowUpEventMetadata.of(b -> b.batchOperationReference(batchOperation.getKey())));
  }

  private void appendBatchOperationExecutionCompletedEvent(
      final TypedRecord<BatchOperationExecutionRecord> command) {
    final var executionRecord = command.getValue();
    final var batchInternalComplete =
        new BatchOperationPartitionLifecycleRecord()
            .setBatchOperationKey(executionRecord.getBatchOperationKey())
            .setSourcePartitionId(command.getPartitionId());

    LOGGER.debug(
        "Send internal complete command for batch operation {} to lead partition {}",
        executionRecord.getBatchOperationKey(),
        getLeadPartition(executionRecord));

    if (isLeadPartition(command)) {
      // If we are the lead partition, we can directly append the follow-up command
      commandWriter.appendFollowUpCommand(
          executionRecord.getBatchOperationKey(),
          BatchOperationIntent.COMPLETE_PARTITION,
          batchInternalComplete,
          FollowUpCommandMetadata.of(
              b -> b.batchOperationReference(executionRecord.getBatchOperationKey())));
    } else {
      // If we are not the lead partition, we need to distribute the command to the lead partition
      // we also append a local follow-up PARTITION_COMPLETED event to mark the partition locally as
      // completed
      stateWriter.appendFollowUpEvent(
          executionRecord.getBatchOperationKey(),
          BatchOperationIntent.PARTITION_COMPLETED,
          batchInternalComplete,
          FollowUpEventMetadata.of(
              b -> b.batchOperationReference(executionRecord.getBatchOperationKey())));
      commandDistributionBehavior
          .withKey(keyGenerator.nextKey())
          .inQueue(DistributionQueue.BATCH_OPERATION)
          .forPartition(getLeadPartition(executionRecord))
          .distribute(
              ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
              BatchOperationIntent.COMPLETE_PARTITION,
              batchInternalComplete,
              command.getAuthInfo());
    }
  }

  /**
   * Returns the lead partition ID for the given batch operation record value. The lead partition is
   * the partition the batch operation was originally created on.
   *
   * @param recordValue the batch operation record value
   * @return the lead partition ID
   */
  private static int getLeadPartition(final BatchOperationRelated recordValue) {
    return Protocol.decodePartitionId(recordValue.getBatchOperationKey());
  }

  /**
   * Checks if the given partition ID is the lead partition for the given batch operation.
   *
   * @param command the batch operation related command
   * @return {@code true} if the record is on its lead partition, {@code false} otherwise
   */
  private static boolean isLeadPartition(final TypedRecord<BatchOperationExecutionRecord> command) {
    return getLeadPartition(command.getValue()) == command.getPartitionId();
  }
}
