/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import java.util.stream.Stream;

public class ProcessDefinitionSuspensionRecordStream
    extends ExporterRecordStream<
        ProcessDefinitionSuspensionRecordValue, ProcessDefinitionSuspensionRecordStream> {

  public ProcessDefinitionSuspensionRecordStream(
      final Stream<Record<ProcessDefinitionSuspensionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  public ProcessDefinitionSuspensionRecordStream withProcessDefinitionKey(
      final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public ProcessDefinitionSuspensionRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> v.getBpmnProcessId().equals(bpmnProcessId));
  }

  public ProcessDefinitionSuspensionRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }

  @Override
  protected ProcessDefinitionSuspensionRecordStream supply(
      final Stream<Record<ProcessDefinitionSuspensionRecordValue>> wrappedStream) {
    return new ProcessDefinitionSuspensionRecordStream(wrappedStream);
  }
}
