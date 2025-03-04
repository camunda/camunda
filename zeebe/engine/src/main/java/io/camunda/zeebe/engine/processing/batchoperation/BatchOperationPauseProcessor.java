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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationPauseProcessor
    implements DistributedTypedRecordProcessor<BatchOperationExecutionRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      BatchOperationPauseProcessor.class);

  private static final String MESSAGE_PREFIX =
      "Expected to pause a batch operation with key '%d', but ";
  private static final String BATCH_OPERATION_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such batch operation was found";

  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;

  private final BatchOperationState batchOperationState;

  public BatchOperationPauseProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior,
      final ProcessingState processingState
  ) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    batchOperationState = processingState.getBatchOperationState();
  }

  @Override
  public void processNewCommand(final TypedRecord<BatchOperationExecutionRecord> command) {
    final var recordValue = command.getValue();
    final var batchKey = command.getValue().getBatchOperationKey();
    LOGGER.debug("Processing new command with key '{}': {}", command.getKey(), recordValue);

    // validation
    final var batchOperation = batchOperationState.get(batchKey).orElse(null);
    if (batchOperation == null
        || !batchOperation.canPause()) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          String.format(BATCH_OPERATION_NOT_FOUND_MESSAGE, batchKey));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.NOT_FOUND,
          String.format(BATCH_OPERATION_NOT_FOUND_MESSAGE, batchKey));
      return;
    }

    pauseBatchOperation(command);
    responseWriter.writeEventOnCommand(
        batchKey,
        BatchOperationIntent.PAUSED,
        command.getValue(),
        command);
    final var key = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(key)
        .unordered()
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<BatchOperationExecutionRecord> command) {
    final var recordValue = command.getValue();

    LOGGER.debug("Processing distributed command with key '{}': {}",
        command.getValue().getBatchOperationKey(), recordValue);
    pauseBatchOperation(command);
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void pauseBatchOperation(final TypedRecord<BatchOperationExecutionRecord> command) {
    stateWriter.appendFollowUpEvent(command.getValue().getBatchOperationKey(),
        BatchOperationIntent.PAUSED,
        command.getValue());
  }

}
