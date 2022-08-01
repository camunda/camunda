/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.stream.Stream;

public final class ProcessInstanceRecordStream
    extends ExporterRecordStream<ProcessInstanceRecordValue, ProcessInstanceRecordStream> {

  public ProcessInstanceRecordStream(final Stream<Record<ProcessInstanceRecordValue>> stream) {
    super(stream);
  }

  @Override
  protected ProcessInstanceRecordStream supply(
      final Stream<Record<ProcessInstanceRecordValue>> stream) {
    return new ProcessInstanceRecordStream(stream);
  }

  public ProcessInstanceRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public ProcessInstanceRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }

  public ProcessInstanceRecordStream withProcessDefinitionKey(final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public ProcessInstanceRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public ProcessInstanceRecordStream withElementId(final String elementId) {
    return valueFilter(v -> elementId.equals(v.getElementId()));
  }

  public ProcessInstanceRecordStream withFlowScopeKey(final long flowScopeKey) {
    return valueFilter(v -> v.getFlowScopeKey() == flowScopeKey);
  }

  public ProcessInstanceRecordStream limitToProcessInstanceCompleted() {
    return limit(
        r ->
            r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                && r.getKey() == r.getValue().getProcessInstanceKey());
  }

  public ProcessInstanceRecordStream limitToProcessInstanceTerminated() {
    return limit(
        r ->
            r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED
                && r.getKey() == r.getValue().getProcessInstanceKey());
  }

  public ProcessInstanceRecordStream limit(
      final String elementId, final ProcessInstanceIntent intent) {
    return limit(
        r -> r.getValue().getElementId().equals(elementId) && r.getIntent().equals(intent));
  }

  public ProcessInstanceRecordStream withElementType(final BpmnElementType elementType) {
    return valueFilter(v -> v.getBpmnElementType() == elementType);
  }

  public ProcessInstanceRecordStream withParentProcessInstanceKey(
      final long parentProcessInstanceKey) {
    return valueFilter(v -> v.getParentProcessInstanceKey() == parentProcessInstanceKey);
  }

  public ProcessInstanceRecordStream withParentElementInstanceKey(
      final long parentElementInstanceKey) {
    return valueFilter(v -> v.getParentElementInstanceKey() == parentElementInstanceKey);
  }

  /**
   * @return stream with only records for the process instance (i.e. root scope of the instance)
   */
  public ProcessInstanceRecordStream filterRootScope() {
    return filter(r -> r.getKey() == r.getValue().getProcessInstanceKey());
  }
}
