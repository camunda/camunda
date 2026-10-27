/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.stream.Stream;

public class BatchOperationCreationRecordStream
    extends ExporterRecordStream<
        BatchOperationCreationRecordValue, BatchOperationCreationRecordStream> {

  public BatchOperationCreationRecordStream(
      final Stream<Record<BatchOperationCreationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected BatchOperationCreationRecordStream supply(
      final Stream<Record<BatchOperationCreationRecordValue>> wrappedStream) {
    return new BatchOperationCreationRecordStream(wrappedStream);
  }

  public BatchOperationCreationRecordStream withBatchOperationType(final BatchOperationType type) {
    return valueFilter(v -> v.getBatchOperationType() == type);
  }

  public BatchOperationCreationRecordStream withBatchOperationKey(final long key) {
    return valueFilter(v -> v.getBatchOperationKey() == key);
  }
}
