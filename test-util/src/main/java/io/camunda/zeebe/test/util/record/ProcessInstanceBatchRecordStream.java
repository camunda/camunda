/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import java.util.stream.Stream;

public class ProcessInstanceBatchRecordStream
    extends ExporterRecordStream<
        ProcessInstanceBatchRecordValue, ProcessInstanceBatchRecordStream> {

  public ProcessInstanceBatchRecordStream(
      final Stream<Record<ProcessInstanceBatchRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ProcessInstanceBatchRecordStream supply(
      final Stream<Record<ProcessInstanceBatchRecordValue>> wrappedStream) {
    return new ProcessInstanceBatchRecordStream(wrappedStream);
  }

  public ProcessInstanceBatchRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public ProcessInstanceBatchRecordStream withBatchElementInstanceKey(
      final long batchElementInstanceKey) {
    return valueFilter(v -> v.getBatchElementInstanceKey() == batchElementInstanceKey);
  }

  public ProcessInstanceBatchRecordStream withIndex(final long index) {
    return valueFilter(v -> v.getIndex() == index);
  }
}
