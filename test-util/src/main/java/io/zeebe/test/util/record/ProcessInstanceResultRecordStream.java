/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import java.util.stream.Stream;

public final class ProcessInstanceResultRecordStream
    extends ExporterRecordStream<
        ProcessInstanceResultRecordValue, ProcessInstanceResultRecordStream> {

  public ProcessInstanceResultRecordStream(
      final Stream<Record<ProcessInstanceResultRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ProcessInstanceResultRecordStream supply(
      final Stream<Record<ProcessInstanceResultRecordValue>> wrappedStream) {
    return new ProcessInstanceResultRecordStream(wrappedStream);
  }

  public ProcessInstanceResultRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public ProcessInstanceResultRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> processInstanceKey == v.getProcessInstanceKey());
  }
}
