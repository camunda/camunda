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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationPartitionFailProcessor
    implements DistributedTypedRecordProcessor<BatchOperationPartitionLifecycleRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationPartitionFailProcessor.class);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final BatchOperationState batchOperationState;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public BatchOperationPartitionFailProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    batchOperationState = processingState.getBatchOperationState();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  /**
   * Processes a non-distributed command to mark a partition of a batch operation as failed.
   *
   * @param command the not yet distributed command to process
   */
  @Override
  public void processNewCommand(final TypedRecord<BatchOperationPartitionLifecycleRecord> command) {
    doProcessRecord(command);
  }

  @Override
  public void processDistributedCommand(
      final TypedRecord<BatchOperationPartitionLifecycleRecord> command) {
    doProcessRecord(command);
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void doProcessRecord(final TypedRecord<BatchOperationPartitionLifecycleRecord> command) {
    final var batchOperationKey = command.getValue().getBatchOperationKey();
    LOGGER.debug(
        "Processing command from partition {} to mark batch operation {} as failed",
        command.getValue().getSourcePartitionId(),
        command.getValue().getBatchOperationKey());

    final var oBatchOperation = batchOperationState.get(batchOperationKey);
    if (oBatchOperation.isPresent()) {
      final var bo = oBatchOperation.get();
      if (bo.getFinishedPartitions().contains(command.getValue().getSourcePartitionId())) {
        LOGGER.debug(
            "Batch operation {} already contains partition {} as finished, ignoring command",
            batchOperationKey,
            command.getValue().getSourcePartitionId());
      } else {

        stateWriter.appendFollowUpEvent(
            batchOperationKey, BatchOperationIntent.PARTITION_FAILED, command.getValue());

        if (bo.getFinishedPartitions().size() == bo.getPartitions().size()) {
          LOGGER.debug(
              "All partitions finished, appending COMPLETE command for batch operation {}",
              batchOperationKey);
          final var batchFinished = new BatchOperationLifecycleManagementRecord();
          batchFinished.setBatchOperationKey(batchOperationKey);
          commandWriter.appendFollowUpCommand(
              batchOperationKey, BatchOperationIntent.COMPLETE, batchFinished);
        }
      }
    }
  }
}
