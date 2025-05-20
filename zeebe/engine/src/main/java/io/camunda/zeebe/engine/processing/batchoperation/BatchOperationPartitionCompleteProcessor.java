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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationPartitionCompleteProcessor
    implements TypedRecordProcessor<BatchOperationLifecycleManagementRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationPartitionCompleteProcessor.class);

  private final StateWriter stateWriter;
  private final BatchOperationState batchOperationState;

  public BatchOperationPartitionCompleteProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    batchOperationState = processingState.getBatchOperationState();
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var batchOperationKey = command.getValue().getBatchOperationKey();
    LOGGER.debug(
        "Processing command from partition {} to mark batch operation {} as completed",
        command.getValue().getSourcePartitionId(),
        command.getValue().getBatchOperationKey());

    stateWriter.appendFollowUpEvent(
        batchOperationKey, BatchOperationIntent.COMPLETED_PARTITION, command.getValue());

    final var oBatchOperation = batchOperationState.get(batchOperationKey);
    oBatchOperation.ifPresent(
        bo -> {
          if (bo.getCompletedPartitions().size() == bo.getPartitions().size()) {
            LOGGER.debug(
                "All partitions completed, appending COMPLETED event for batch operation {}",
                batchOperationKey);
            final var batchExecute = new BatchOperationExecutionRecord();
            batchExecute.setBatchOperationKey(batchOperationKey);
            stateWriter.appendFollowUpEvent(
                batchOperationKey, BatchOperationExecutionIntent.COMPLETED, batchExecute);
          }
        });
  }
}
