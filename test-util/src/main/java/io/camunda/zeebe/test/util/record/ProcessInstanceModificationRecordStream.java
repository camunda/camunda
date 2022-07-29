/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import java.util.stream.Stream;

public final class ProcessInstanceModificationRecordStream
    extends ExporterRecordStream<
        ProcessInstanceModificationRecordValue, ProcessInstanceModificationRecordStream> {

  public ProcessInstanceModificationRecordStream(
      final Stream<Record<ProcessInstanceModificationRecordValue>> records) {
    super(records);
  }

  @Override
  protected ProcessInstanceModificationRecordStream supply(
      final Stream<Record<ProcessInstanceModificationRecordValue>> wrappedStream) {
    return new ProcessInstanceModificationRecordStream(wrappedStream);
  }

  public ProcessInstanceModificationRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }
}
