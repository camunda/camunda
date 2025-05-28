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
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationCompleteProcessor
    implements DistributedTypedRecordProcessor<BatchOperationLifecycleManagementRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationCompleteProcessor.class);

  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final BatchOperationState batchOperationState;

  public BatchOperationCompleteProcessor(
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ProcessingState processingState,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    this.commandDistributionBehavior = commandDistributionBehavior;
    batchOperationState = processingState.getBatchOperationState();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processNewCommand(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var batchOperationKey = command.getValue().getBatchOperationKey();

    // validation
    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isEmpty()) {
      LOGGER.info(
          "Trying to complete batch operation with key '{}', but it was not found. Ignoring it.",
          batchOperationKey);
      return;
    }

    final var completeKey = keyGenerator.nextKey();
    completeBatchOperation(completeKey, command.getValue());
    commandDistributionBehavior
        .withKey(completeKey)
        .inQueue(DistributionQueue.BATCH_OPERATION)
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var recordValue = command.getValue();
    final var batchOperationKey = recordValue.getBatchOperationKey();

    // Validation
    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isPresent()) {
      LOGGER.debug(
          "Processing distributed command to complete with key '{}': {}",
          batchOperationKey,
          recordValue);
      completeBatchOperation(batchOperationKey, recordValue);
    } else {
      LOGGER.debug(
          "Distributed command to complete batch operation with key '{}' will be ignored: {}",
          batchOperationKey,
          recordValue);
    }

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void completeBatchOperation(
      final Long completeKey, final BatchOperationLifecycleManagementRecord recordValue) {
    stateWriter.appendFollowUpEvent(completeKey, BatchOperationIntent.COMPLETED, recordValue);
  }
}
