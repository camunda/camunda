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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationResumeProcessor
    implements DistributedTypedRecordProcessor<BatchOperationLifecycleManagementRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationResumeProcessor.class);

  private static final String MESSAGE_PREFIX =
      "Expected to resume a batch operation with key '%d', but ";
  private static final String BATCH_OPERATION_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such batch operation was found";
  private static final String BATCH_OPERATION_INVALID_STATE_MESSAGE =
      MESSAGE_PREFIX + "it has an invalid state '%s'.";

  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;

  private final BatchOperationState batchOperationState;

  public BatchOperationResumeProcessor(
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ProcessingState processingState,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    batchOperationState = processingState.getBatchOperationState();
  }

  @Override
  public void processNewCommand(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var recordValue = command.getValue();
    final var batchOperationKey = command.getValue().getBatchOperationKey();
    final var resumeKey = keyGenerator.nextKey();
    LOGGER.debug(
        "Processing new command to resume a batch operation with key '{}': {}",
        command.getKey(),
        recordValue);

    // validation
    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isEmpty()) {
      rejectNotFound(command, batchOperationKey, recordValue);
      return;
    }

    // check if the batch operation can be resumed
    if (!batchOperation.get().canResume()) {
      final var batchOperationStatus = batchOperation.get().getStatus().name();
      rejectInvalidState(command, batchOperationKey, batchOperationStatus, recordValue);
      return;
    }

    resumeBatchOperation(resumeKey, batchOperationKey, command.getValue());
    commandDistributionBehavior.withKey(resumeKey).unordered().distribute(command);
  }

  @Override
  public void processDistributedCommand(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var recordValue = command.getValue();
    final var batchOperationKey = recordValue.getBatchOperationKey();

    // Validation
    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isPresent() && batchOperation.get().canResume()) {
      LOGGER.debug(
          "Processing distributed command to resume with key '{}': {}",
          batchOperationKey,
          recordValue);
      resumeBatchOperation(command.getKey(), batchOperationKey, recordValue);
    } else {
      LOGGER.debug(
          "Distributed command to resume a batch operation with key '{}' will be ignored: {}",
          batchOperationKey,
          recordValue);
    }
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void resumeBatchOperation(
      final Long resumeKey,
      final Long batchOperationKey,
      final BatchOperationLifecycleManagementRecord recordValue) {
    stateWriter.appendFollowUpEvent(resumeKey, BatchOperationIntent.RESUMED, recordValue);

    final var batchExecute = new BatchOperationExecutionRecord();
    batchExecute.setBatchOperationKey(batchOperationKey);
    commandWriter.appendFollowUpCommand(
        batchOperationKey, BatchOperationExecutionIntent.EXECUTE, batchExecute);
  }

  private void rejectInvalidState(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command,
      final long batchOperationKey,
      final String batchOperationStatus,
      final BatchOperationLifecycleManagementRecord recordValue) {
    LOGGER.info(
        "Batch operation with key '{}' cannot be resumed because of invalid status '{}', rejecting command: {}",
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
