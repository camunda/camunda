/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.SuspensionBatchRecordValue;
import java.util.stream.Stream;

public class SuspensionBatchRecordStream
    extends ExporterRecordStream<SuspensionBatchRecordValue, SuspensionBatchRecordStream> {

  public SuspensionBatchRecordStream(
      final Stream<Record<SuspensionBatchRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected SuspensionBatchRecordStream supply(
      final Stream<Record<SuspensionBatchRecordValue>> wrappedStream) {
    return new SuspensionBatchRecordStream(wrappedStream);
  }

  public SuspensionBatchRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public SuspensionBatchRecordStream withIndexKey(final long indexKey) {
    return valueFilter(v -> v.getIndexKey() == indexKey);
  }

  public SuspensionBatchRecordStream withParentKey(final long parentKey) {
    return valueFilter(v -> v.getParentKey() == parentKey);
  }
}
