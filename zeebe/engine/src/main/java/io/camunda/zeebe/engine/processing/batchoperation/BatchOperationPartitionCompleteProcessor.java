/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
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
public final class BatchOperationPartitionCompleteProcessor
    implements DistributedTypedRecordProcessor<BatchOperationPartitionLifecycleRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationPartitionCompleteProcessor.class);

  private final StateWriter stateWriter;
  private final BatchOperationState batchOperationState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final int partitionId;

  public BatchOperationPartitionCompleteProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final int partitionId) {
    stateWriter = writers.state();
    batchOperationState = processingState.getBatchOperationState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.partitionId = partitionId;
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
    if (Protocol.decodePartitionId(batchOperationKey) != partitionId) {
      LOGGER.warn(
          "Received command for batch operation {} on partition {}, but partition {} is leader for this batch operation. Ignoring command.",
          batchOperationKey,
          Protocol.decodePartitionId(batchOperationKey),
          partitionId);
      return;
    }

    LOGGER.debug(
        "Processing command from partition {} to mark batch operation {} as completed",
        command.getValue().getSourcePartitionId(),
        command.getValue().getBatchOperationKey());

    final var oBatchOperation = batchOperationState.get(batchOperationKey);
    if (oBatchOperation.isPresent()) {
      final var bo = oBatchOperation.get();
      if (bo.getCompletedPartitions().contains(command.getValue().getSourcePartitionId())) {
        LOGGER.debug(
            "Batch operation {} already contains partition {}, ignoring command",
            batchOperationKey,
            command.getValue().getSourcePartitionId());
      } else {
        stateWriter.appendFollowUpEvent(
            batchOperationKey, BatchOperationIntent.PARTITION_COMPLETED, command.getValue());

        if (bo.getCompletedPartitions().containsAll(bo.getPartitions())) {
          LOGGER.debug(
              "All partitions completed, appending COMPLETED event for batch operation {}",
              batchOperationKey);
          final var batchComplete = new BatchOperationLifecycleManagementRecord();
          batchComplete.setBatchOperationKey(batchOperationKey);
          stateWriter.appendFollowUpEvent(
              batchOperationKey, BatchOperationIntent.COMPLETED, batchComplete);
        }
      }
    }
  }
}
