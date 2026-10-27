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
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This processor only runs on the lead partition of a batch operation. It processes commands to
 * mark follower partitions as completed.
 */
@ExcludeAuthorizationCheck
public final class BatchOperationPartitionLifecycleCompletePartitionProcessor
    implements DistributedTypedRecordProcessor<BatchOperationPartitionLifecycleRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationPartitionLifecycleCompletePartitionProcessor.class);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final BatchOperationState batchOperationState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final BatchOperationMetrics metrics;

  public BatchOperationPartitionLifecycleCompletePartitionProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BatchOperationMetrics metrics) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    batchOperationState = processingState.getBatchOperationState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.metrics = metrics;
  }

  /**
   * Processes a non-distributed command to mark a partition of a batch operation as completed. This
   * occurs, when the leader marks itself as completed.
   *
   * @param command the command to process
   */
  @Override
  public void processNewCommand(final TypedRecord<BatchOperationPartitionLifecycleRecord> command) {
    doProcessRecord(command);
  }

  /**
   * Processes a command from a follower partition to mark that partition of a batch operation as
   * completed.
   *
   * @param command the command to process
   */
  @Override
  public void processDistributedCommand(
      final TypedRecord<BatchOperationPartitionLifecycleRecord> command) {
    doProcessRecord(command);

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void doProcessRecord(final TypedRecord<BatchOperationPartitionLifecycleRecord> command) {
    final var batchOperationKey = command.getValue().getBatchOperationKey();
    LOGGER.debug(
        "Processing command from partition {} to mark batch operation {} as completed",
        command.getValue().getSourcePartitionId(),
        command.getValue().getBatchOperationKey());

    final var oBatchOperation = batchOperationState.get(batchOperationKey);
    if (oBatchOperation.isEmpty()) {
      return;
    }

    final var bo = oBatchOperation.get();
    if (bo.getFinishedPartitions().contains(command.getValue().getSourcePartitionId())) {
      LOGGER.debug(
          "Batch operation {} already contains partition {}, ignoring command",
          batchOperationKey,
          command.getValue().getSourcePartitionId());
      return;
    }

    // mark the source partition as finished. This information is directly applied and present
    // in the batch operation state
    stateWriter.appendFollowUpEvent(
        batchOperationKey,
        BatchOperationIntent.PARTITION_COMPLETED,
        command.getValue(),
        FollowUpEventMetadata.of(
            b -> b.batchOperationReference(command.getValue().getBatchOperationKey())));

    // after the source partition is marked as finished, we check if now all partitions are
    // finished (either completed or failed). If yes, we can append the final COMPLETED event
    if (bo.getFinishedPartitions().size() == bo.getPartitions().size()) {
      handleCompleted(command, batchOperationKey, bo);
    }
  }

  private void handleCompleted(
      final TypedRecord<BatchOperationPartitionLifecycleRecord> command,
      final long batchOperationKey,
      final PersistedBatchOperation bo) {
    final var metadata =
        FollowUpEventMetadata.of(
            b -> b.batchOperationReference(command.getValue().getBatchOperationKey()));

    final var batchCompleted = new BatchOperationLifecycleManagementRecord();
    batchCompleted.setBatchOperationKey(batchOperationKey);
    if (!bo.getErrors().isEmpty()) {
      LOGGER.debug(
          "Some partitions ({}) finished with errors, appending them to COMPLETED event for batch operation {}",
          bo.getErrors().size(),
          batchOperationKey);
      batchCompleted.setErrors(bo.getErrors());
    }

    if (bo.hasFollowUpCommand()) {
      final var followUpCommand = bo.getFollowUpCommand();
      commandWriter.appendFollowUpCommand(
          batchOperationKey, followUpCommand.getIntent(), followUpCommand.getRecordValue());
    }

    LOGGER.debug(
        "All partitions finished, appending COMPLETED event for batch operation {}",
        batchOperationKey);
    stateWriter.appendFollowUpEvent(
        batchOperationKey, BatchOperationIntent.COMPLETED, batchCompleted, metadata);

    metrics.recordCompleted(bo.getBatchOperationType());
    metrics.stopTotalDurationMeasure(batchOperationKey);
  }
}
