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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
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
 * mark follower partitions as failed.
 */
@ExcludeAuthorizationCheck
public final class BatchOperationLeadPartitionFailProcessor
    implements DistributedTypedRecordProcessor<BatchOperationPartitionLifecycleRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationLeadPartitionFailProcessor.class);

  private final StateWriter stateWriter;
  private final BatchOperationState batchOperationState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final BatchOperationMetrics batchOperationMetrics;

  public BatchOperationLeadPartitionFailProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BatchOperationMetrics batchOperationMetrics) {
    stateWriter = writers.state();
    batchOperationState = processingState.getBatchOperationState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.batchOperationMetrics = batchOperationMetrics;
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
        "Processing command from partition {} to mark batch operation {} as failed with error type {}",
        command.getValue().getSourcePartitionId(),
        command.getValue().getBatchOperationKey(),
        command.getValue().getError().getType());

    final var oBatchOperation = batchOperationState.get(batchOperationKey);
    if (oBatchOperation.isEmpty()) {
      LOGGER.debug(
          "Expected to mark batch operation {} as failed, but it does not exist",
          batchOperationKey);
      return;
    }

    final var bo = oBatchOperation.get();
    if (bo.getFinishedPartitions().contains(command.getValue().getSourcePartitionId())) {
      LOGGER.debug(
          "Batch operation {} already contains partition {} as finished, ignoring command",
          batchOperationKey,
          command.getValue().getSourcePartitionId());
      return;
    }

    // we need this event for every partition that failed, so that the lead partition state can
    // collect all of them. That's why in this case, we don't append this event in the normal
    // FailProcessor. (Would be duplicated otherwise)
    // This information is directly applied and present in the batch operation state
    stateWriter.appendFollowUpEvent(
        batchOperationKey,
        BatchOperationIntent.PARTITION_FAILED,
        command.getValue(),
        FollowUpEventMetadata.of(b -> b.batchOperationReference(batchOperationKey)));

    // after the source partition is marked as finished, we check if now all partitions are
    // finished (either completed or failed). If yes, we can append the final COMPLETED event
    if (bo.getFinishedPartitions().size() == bo.getPartitions().size()) {
      BatchOperationIntent intent = BatchOperationIntent.COMPLETED;
      if (bo.getPartitions().size() == bo.getErrors().size()) {
        // all partitions failed
        intent = BatchOperationIntent.FAILED;
      }

      LOGGER.debug(
          "All partitions finished, but some with errors, appending {} event with errors for batch operation {}",
          intent.name(),
          batchOperationKey);
      final var batchFinished = new BatchOperationLifecycleManagementRecord();
      batchFinished.setBatchOperationKey(batchOperationKey);
      batchFinished.setErrors(bo.getErrors());
      stateWriter.appendFollowUpEvent(
          batchOperationKey,
          intent,
          batchFinished,
          FollowUpEventMetadata.of(
              b -> b.batchOperationReference(command.getValue().getBatchOperationKey())));
      batchOperationMetrics.recordFailed(oBatchOperation.get().getBatchOperationType());
    }
  }
}
