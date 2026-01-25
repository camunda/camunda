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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Processes commands to create batch operation chunks. */
@ExcludeAuthorizationCheck
public final class BatchOperationChunkCreateProcessor
    implements TypedRecordProcessor<BatchOperationChunkRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationChunkCreateProcessor.class);

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public BatchOperationChunkCreateProcessor(
      final Writers writers, final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationChunkRecord> command) {
    final var recordValue = command.getValue();
    LOGGER.debug(
        "Creating a new chunk with {} items for batch operation {}",
        recordValue.getItems().size(),
        recordValue.getBatchOperationKey());

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), BatchOperationChunkIntent.CREATED, recordValue);
  }
}
