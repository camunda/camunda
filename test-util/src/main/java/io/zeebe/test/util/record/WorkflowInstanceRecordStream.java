/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import java.util.stream.Stream;

public final class WorkflowInstanceRecordStream
    extends ExporterRecordStream<WorkflowInstanceRecordValue, WorkflowInstanceRecordStream> {

  public WorkflowInstanceRecordStream(final Stream<Record<WorkflowInstanceRecordValue>> stream) {
    super(stream);
  }

  @Override
  protected WorkflowInstanceRecordStream supply(
      final Stream<Record<WorkflowInstanceRecordValue>> stream) {
    return new WorkflowInstanceRecordStream(stream);
  }

  public WorkflowInstanceRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public WorkflowInstanceRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }

  public WorkflowInstanceRecordStream withWorkflowKey(final long workflowKey) {
    return valueFilter(v -> v.getWorkflowKey() == workflowKey);
  }

  public WorkflowInstanceRecordStream withWorkflowInstanceKey(final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }

  public WorkflowInstanceRecordStream withElementId(final String elementId) {
    return valueFilter(v -> elementId.equals(v.getElementId()));
  }

  public WorkflowInstanceRecordStream withFlowScopeKey(final long flowScopeKey) {
    return valueFilter(v -> v.getFlowScopeKey() == flowScopeKey);
  }

  public WorkflowInstanceRecordStream limitToWorkflowInstanceCompleted() {
    return limit(
        r ->
            r.getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED
                && r.getKey() == r.getValue().getWorkflowInstanceKey());
  }

  public WorkflowInstanceRecordStream limitToWorkflowInstanceTerminated() {
    return limit(
        r ->
            r.getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATED
                && r.getKey() == r.getValue().getWorkflowInstanceKey());
  }

  public WorkflowInstanceRecordStream withElementType(final BpmnElementType elementType) {
    return valueFilter(v -> v.getBpmnElementType() == elementType);
  }

  public WorkflowInstanceRecordStream withParentWorkflowInstanceKey(
      final long parentWorkflowInstanceKey) {
    return valueFilter(v -> v.getParentWorkflowInstanceKey() == parentWorkflowInstanceKey);
  }

  public WorkflowInstanceRecordStream withParentElementInstanceKey(
      final long parentElementInstanceKey) {
    return valueFilter(v -> v.getParentElementInstanceKey() == parentElementInstanceKey);
  }

  /**
   * @return stream with only records for the workflow instance (i.e. root scope of the instance)
   */
  public WorkflowInstanceRecordStream filterRootScope() {
    return filter(r -> r.getKey() == r.getValue().getWorkflowInstanceKey());
  }
}
