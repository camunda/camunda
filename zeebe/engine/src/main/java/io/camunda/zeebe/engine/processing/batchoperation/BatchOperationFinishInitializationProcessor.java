/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationFinishInitializationProcessor
    implements TypedRecordProcessor<BatchOperationInitializationRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationFinishInitializationProcessor.class);

  private final StateWriter stateWriter;
  private final BatchOperationMetrics metrics;

  public BatchOperationFinishInitializationProcessor(
      final Writers writers, final BatchOperationMetrics metrics) {
    stateWriter = writers.state();
    this.metrics = metrics;
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationInitializationRecord> command) {
    final var initializationRecord = command.getValue();
    LOGGER.debug(
        "Marking batch operation {} as initialized", command.getValue().getBatchOperationKey());
    stateWriter.appendFollowUpEvent(
        command.getKey(), BatchOperationIntent.INITIALIZED, initializationRecord);
  }
}
