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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationCreateProcessor
    implements DistributedTypedRecordProcessor<BatchOperationCreationRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationCreateProcessor.class);

  private static final String EMPTY_JSON_OBJECT = "{}";
  private static final String MESSAGE_GIVEN_FILTER_IS_EMPTY = "Given filter is empty";

  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public BatchOperationCreateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<BatchOperationCreationRecord> command) {
    if (isEmptyOrNullFilter(command)) {
      rejectionWriter.appendRejection(
          command, RejectionType.INVALID_ARGUMENT, MESSAGE_GIVEN_FILTER_IS_EMPTY);
      return;
    }

    final long key = keyGenerator.nextKey();
    final var recordValue = command.getValue();
    LOGGER.debug("Processing new command with key '{}': {}", key, recordValue);

    final var recordWithKey = new BatchOperationCreationRecord();
    recordWithKey.setBatchOperationKey(key);
    recordWithKey.setBatchOperationType(recordValue.getBatchOperationType());
    recordWithKey.setEntityFilter(command.getValue().getEntityFilterBuffer());

    stateWriter.appendFollowUpEvent(key, BatchOperationIntent.CREATED, recordWithKey);
    responseWriter.writeEventOnCommand(key, BatchOperationIntent.CREATED, recordWithKey, command);
    commandDistributionBehavior
        .withKey(key)
        .unordered()
        .distribute(command.getValueType(), command.getIntent(), recordWithKey);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<BatchOperationCreationRecord> command) {
    final var recordValue = command.getValue();

    LOGGER.debug("Processing distributed command with key '{}': {}", command.getKey(), recordValue);
    stateWriter.appendFollowUpEvent(
        command.getKey(), BatchOperationIntent.CREATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private static boolean isEmptyOrNullFilter(
      final TypedRecord<BatchOperationCreationRecord> command) {
    return command.getValue().getEntityFilter() == null
        || command.getValue().getEntityFilter().equalsIgnoreCase(EMPTY_JSON_OBJECT);
  }
}
