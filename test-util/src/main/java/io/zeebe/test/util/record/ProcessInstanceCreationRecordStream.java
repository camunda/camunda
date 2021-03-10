/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.test.util.collection.Maps;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class WorkflowInstanceCreationRecordStream
    extends ExporterRecordStream<
        WorkflowInstanceCreationRecordValue, WorkflowInstanceCreationRecordStream> {

  public WorkflowInstanceCreationRecordStream(
      final Stream<Record<WorkflowInstanceCreationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected WorkflowInstanceCreationRecordStream supply(
      final Stream<Record<WorkflowInstanceCreationRecordValue>> wrappedStream) {
    return new WorkflowInstanceCreationRecordStream(wrappedStream);
  }

  public WorkflowInstanceCreationRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> v.getBpmnProcessId().equals(bpmnProcessId));
  }

  public WorkflowInstanceCreationRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }

  public WorkflowInstanceCreationRecordStream withKey(final long key) {
    return valueFilter(v -> v.getWorkflowKey() == key);
  }

  public WorkflowInstanceCreationRecordStream withInstanceKey(final long instanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == instanceKey);
  }

  public WorkflowInstanceCreationRecordStream withVariables(final Map<String, Object> variables) {
    return valueFilter(v -> v.getVariables().equals(variables));
  }

  public WorkflowInstanceCreationRecordStream withVariables(
      final Map.Entry<String, Object>... entries) {
    return withVariables(Maps.of(entries));
  }

  public WorkflowInstanceCreationRecordStream withVariables(
      final Predicate<Map<String, Object>> matcher) {
    return valueFilter(v -> matcher.test(v.getVariables()));
  }

  public WorkflowInstanceCreationRecordStream limitToWorkflowInstanceCreated(
      final long workflowInstanceKey) {
    return limit(
        r ->
            r.getIntent() == WorkflowInstanceCreationIntent.CREATED
                && r.getValue().getWorkflowInstanceKey() == workflowInstanceKey);
  }
}
