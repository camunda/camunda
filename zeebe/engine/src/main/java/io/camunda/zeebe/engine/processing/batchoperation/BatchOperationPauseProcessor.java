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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationPauseProcessor
    implements DistributedTypedRecordProcessor<BatchOperationLifecycleManagementRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationPauseProcessor.class);

  private static final String MESSAGE_PREFIX =
      "Expected to pause a batch operation with key '%d', but ";
  private static final String BATCH_OPERATION_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such batch operation was found.";
  private static final String BATCH_OPERATION_INVALID_STATE_MESSAGE =
      MESSAGE_PREFIX + "it has an invalid state '%s'.";

  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final BatchOperationState batchOperationState;

  public BatchOperationPauseProcessor(
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ProcessingState processingState,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.commandDistributionBehavior = commandDistributionBehavior;
    batchOperationState = processingState.getBatchOperationState();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processNewCommand(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var recordValue = command.getValue();
    final var batchOperationKey = command.getValue().getBatchOperationKey();
    final var pauseKey = keyGenerator.nextKey();
    LOGGER.debug(
        "Processing new command to pause batch operation with key '{}': {}",
        command.getKey(),
        recordValue);

    // validation
    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isEmpty()) {
      rejectNotFound(command, batchOperationKey, recordValue);
      return;
    }

    // check if the batch operation can be paused
    if (!batchOperation.get().canPause()) {
      final var batchOperationStatus = batchOperation.get().getStatus().name();
      rejectInvalidState(command, batchOperationKey, batchOperationStatus, recordValue);
      return;
    }

    pauseBatchOperation(pauseKey, recordValue);
    responseWriter.writeEventOnCommand(
        pauseKey, BatchOperationIntent.PAUSED, command.getValue(), command);
    commandDistributionBehavior.withKey(pauseKey).unordered().distribute(command);
  }

  @Override
  public void processDistributedCommand(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var recordValue = command.getValue();
    final var batchOperationKey = recordValue.getBatchOperationKey();

    // Validation
    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isPresent() && batchOperation.get().canPause()) {
      LOGGER.debug(
          "Processing distributed command to pause with key '{}': {}",
          batchOperationKey,
          recordValue);
      pauseBatchOperation(batchOperationKey, recordValue);
    } else {
      LOGGER.debug(
          "Distributed command to pause batch operation with key '{}' will be ignored: {}",
          batchOperationKey,
          recordValue);
    }

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void pauseBatchOperation(
      final Long pauseKey, final BatchOperationLifecycleManagementRecord recordValue) {
    stateWriter.appendFollowUpEvent(pauseKey, BatchOperationIntent.PAUSED, recordValue);
  }

  private void rejectInvalidState(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command,
      final long batchOperationKey,
      final String batchOperationStatus,
      final BatchOperationLifecycleManagementRecord recordValue) {
    LOGGER.info(
        "Batch operation with key '{}' cannot be paused because of invalid status '{}', rejecting command: {}",
        batchOperationKey,
        batchOperationStatus,
        recordValue);
    rejectionWriter.appendRejection(
        command,
        RejectionType.INVALID_STATE,
        String.format(
            BATCH_OPERATION_INVALID_STATE_MESSAGE, batchOperationKey, batchOperationStatus));
    responseWriter.writeRejectionOnCommand(
        command,
        RejectionType.INVALID_STATE,
        String.format(
            BATCH_OPERATION_INVALID_STATE_MESSAGE, batchOperationKey, batchOperationStatus));
  }

  private void rejectNotFound(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command,
      final long batchOperationKey,
      final BatchOperationLifecycleManagementRecord recordValue) {
    LOGGER.info(
        "Batch operation with key '{}' not found, rejecting command: {}",
        batchOperationKey,
        recordValue);
    rejectionWriter.appendRejection(
        command,
        RejectionType.NOT_FOUND,
        String.format(BATCH_OPERATION_NOT_FOUND_MESSAGE, batchOperationKey));
    responseWriter.writeRejectionOnCommand(
        command,
        RejectionType.NOT_FOUND,
        String.format(BATCH_OPERATION_NOT_FOUND_MESSAGE, batchOperationKey));
  }
}
