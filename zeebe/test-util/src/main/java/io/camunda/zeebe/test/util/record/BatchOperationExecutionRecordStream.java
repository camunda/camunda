/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.stream.Stream;

public class BatchOperationExecutionRecordStream
    extends ExporterRecordStream<
        BatchOperationExecutionRecordValue, BatchOperationExecutionRecordStream> {

  public BatchOperationExecutionRecordStream(
      final Stream<Record<BatchOperationExecutionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected BatchOperationExecutionRecordStream supply(
      final Stream<Record<BatchOperationExecutionRecordValue>> wrappedStream) {
    return new BatchOperationExecutionRecordStream(wrappedStream);
  }

  public BatchOperationExecutionRecordStream withBatchOperationKey(final long batchOperationKey) {
    return valueFilter(v -> v.getBatchOperationKey().equals(batchOperationKey));
  }

  public BatchOperationExecutionRecordStream withType(final BatchOperationType type) {
    return valueFilter(v -> v.getBatchOperationType().equals(type));
  }

  public BatchOperationExecutionRecordStream withEntityKey(final long entityKey) {
    return valueFilter(v -> v.getKeys().contains(entityKey));
  }
}
