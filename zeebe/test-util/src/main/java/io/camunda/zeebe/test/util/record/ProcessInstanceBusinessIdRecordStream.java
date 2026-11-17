/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBusinessIdRecordValue;
import java.util.stream.Stream;

public final class ProcessInstanceBusinessIdRecordStream
    extends ExporterRecordStream<
        ProcessInstanceBusinessIdRecordValue, ProcessInstanceBusinessIdRecordStream> {

  public ProcessInstanceBusinessIdRecordStream(
      final Stream<Record<ProcessInstanceBusinessIdRecordValue>> records) {
    super(records);
  }

  @Override
  protected ProcessInstanceBusinessIdRecordStream supply(
      final Stream<Record<ProcessInstanceBusinessIdRecordValue>> wrappedStream) {
    return new ProcessInstanceBusinessIdRecordStream(wrappedStream);
  }

  public ProcessInstanceBusinessIdRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public ProcessInstanceBusinessIdRecordStream withBusinessId(final String businessId) {
    return valueFilter(v -> businessId.equals(v.getBusinessId()));
  }
}
