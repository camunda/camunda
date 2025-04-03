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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationStartProcessor
    implements TypedRecordProcessor<BatchOperationCreationRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationStartProcessor.class);

  private final StateWriter stateWriter;

  public BatchOperationStartProcessor(final Writers writers) {
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationCreationRecord> command) {
    final var recordValue = command.getValue();
    LOGGER.debug("Processing new command with key '{}': {}", command.getKey(), recordValue);

    stateWriter.appendFollowUpEvent(command.getKey(), BatchOperationIntent.STARTED, recordValue);
  }
}
