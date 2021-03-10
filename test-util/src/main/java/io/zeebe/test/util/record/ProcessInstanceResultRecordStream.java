/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.WorkflowInstanceResultRecordValue;
import java.util.stream.Stream;

public final class WorkflowInstanceResultRecordStream
    extends ExporterRecordStream<
        WorkflowInstanceResultRecordValue, WorkflowInstanceResultRecordStream> {

  public WorkflowInstanceResultRecordStream(
      final Stream<Record<WorkflowInstanceResultRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected WorkflowInstanceResultRecordStream supply(
      final Stream<Record<WorkflowInstanceResultRecordValue>> wrappedStream) {
    return new WorkflowInstanceResultRecordStream(wrappedStream);
  }

  public WorkflowInstanceResultRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public WorkflowInstanceResultRecordStream withWorkflowInstanceKey(
      final long workflowInstanceKey) {
    return valueFilter(v -> workflowInstanceKey == v.getWorkflowInstanceKey());
  }
}
