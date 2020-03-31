/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.WorkflowInstanceSubscriptionRecordValue;
import java.util.stream.Stream;

public final class WorkflowInstanceSubscriptionRecordStream
    extends ExporterRecordWithVariablesStream<
        WorkflowInstanceSubscriptionRecordValue, WorkflowInstanceSubscriptionRecordStream> {

  public WorkflowInstanceSubscriptionRecordStream(
      final Stream<Record<WorkflowInstanceSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected WorkflowInstanceSubscriptionRecordStream supply(
      final Stream<Record<WorkflowInstanceSubscriptionRecordValue>> wrappedStream) {
    return new WorkflowInstanceSubscriptionRecordStream(wrappedStream);
  }

  public WorkflowInstanceSubscriptionRecordStream withWorkflowInstanceKey(
      final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }

  public WorkflowInstanceSubscriptionRecordStream withElementInstanceKey(
      final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public WorkflowInstanceSubscriptionRecordStream withMessageName(final String messageName) {
    return valueFilter(v -> messageName.equals(v.getMessageName()));
  }
}
