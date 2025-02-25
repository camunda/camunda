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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationActivateProcessor
    implements TypedRecordProcessor<BatchOperationCreationRecord> {
  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationActivateProcessor.class);

  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;

  public BatchOperationActivateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator) {
    commandWriter = writers.command();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationCreationRecord> record) {
    final var recordValue = record.getValue();

    LOGGER.debug("Processing record: {}", recordValue);
    // Do something with the record
  }
}
