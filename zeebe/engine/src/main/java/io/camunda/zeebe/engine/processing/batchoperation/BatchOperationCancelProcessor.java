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
public final class BatchOperationCancelProcessor
    implements DistributedTypedRecordProcessor<BatchOperationLifecycleManagementRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationCancelProcessor.class);

  private static final String MESSAGE_PREFIX =
      "Expected to cancel a batch operation with key '%d', but ";
  private static final String BATCH_OPERATION_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such batch operation was found";

  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final BatchOperationState batchOperationState;
  private final KeyGenerator keyGenerator;

  public BatchOperationCancelProcessor(
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
    final var batchOperationKey = recordValue.getBatchOperationKey();
    final var cancelKey = keyGenerator.nextKey();
    LOGGER.debug(
        "Processing new command to cancel a batch operation with key '{}': {}",
        batchOperationKey,
        recordValue);

    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isPresent() && batchOperation.get().canCancel()) {
      cancelBatchOperationEvent(cancelKey, recordValue);
      responseWriter.writeEventOnCommand(
          cancelKey, BatchOperationIntent.CANCELED, command.getValue(), command);
      commandDistributionBehavior.withKey(cancelKey).unordered().distribute(command);
    } else {
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

  @Override
  public void processDistributedCommand(
      final TypedRecord<BatchOperationLifecycleManagementRecord> command) {
    final var recordValue = command.getValue();
    final var batchOperationKey = recordValue.getBatchOperationKey();

    final var batchOperation = batchOperationState.get(batchOperationKey);
    if (batchOperation.isEmpty()) {
      rejectionWriter.appendRejection(
          command, RejectionType.NOT_FOUND, "Batch operation does not exist!");
      commandDistributionBehavior.acknowledgeCommand(command);
      return;
    }

    LOGGER.debug(
        "Processing distributed command to cancel a batch operation with key '{}': {}",
        batchOperationKey,
        recordValue);
    cancelBatchOperationEvent(batchOperationKey, recordValue);
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void cancelBatchOperationEvent(
      final Long cancelKey, final BatchOperationLifecycleManagementRecord recordValue) {
    stateWriter.appendFollowUpEvent(cancelKey, BatchOperationIntent.CANCELED, recordValue);
  }
}
