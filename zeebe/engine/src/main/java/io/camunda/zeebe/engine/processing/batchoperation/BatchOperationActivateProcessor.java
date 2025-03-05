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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationActivateProcessor
    implements DistributedTypedRecordProcessor<BatchOperationCreationRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationActivateProcessor.class);

  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;

  public BatchOperationActivateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<BatchOperationCreationRecord> command) {
    final long key = keyGenerator.nextKey();
    final var recordValue = command.getValue();
    LOGGER.debug("Processing new command with key '{}': {}", key, recordValue);

    final var recordWithKey = new BatchOperationCreationRecord();
    recordWithKey.wrap(command.getValue());
    recordWithKey.setBatchOperationKey(key);

    stateWriter.appendFollowUpEvent(key, BatchOperationIntent.CREATED, recordWithKey);
    responseWriter.writeEventOnCommand(
        key, BatchOperationIntent.CREATED, recordWithKey, command);
    commandDistributionBehavior.withKey(key).unordered().distribute(
        command.getValueType(),
        command.getIntent(),
        recordWithKey
    );
  }

  @Override
  public void processDistributedCommand(final TypedRecord<BatchOperationCreationRecord> command) {
    final var recordValue = command.getValue();

    LOGGER.debug("Processing distributed command with key '{}': {}", command.getKey(), recordValue);
    stateWriter.appendFollowUpEvent(
        command.getKey(), BatchOperationIntent.CREATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
