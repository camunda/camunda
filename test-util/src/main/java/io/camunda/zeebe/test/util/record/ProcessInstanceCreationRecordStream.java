/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationStartInstructionValue;
import io.camunda.zeebe.test.util.collection.Maps;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ProcessInstanceCreationRecordStream
    extends ExporterRecordStream<
        ProcessInstanceCreationRecordValue, ProcessInstanceCreationRecordStream> {

  public ProcessInstanceCreationRecordStream(
      final Stream<Record<ProcessInstanceCreationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ProcessInstanceCreationRecordStream supply(
      final Stream<Record<ProcessInstanceCreationRecordValue>> wrappedStream) {
    return new ProcessInstanceCreationRecordStream(wrappedStream);
  }

  public ProcessInstanceCreationRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> v.getBpmnProcessId().equals(bpmnProcessId));
  }

  public ProcessInstanceCreationRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> tenantId.equals(v.getTenantId()));
  }

  public ProcessInstanceCreationRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }

  public ProcessInstanceCreationRecordStream withKey(final long key) {
    return valueFilter(v -> v.getProcessDefinitionKey() == key);
  }

  public ProcessInstanceCreationRecordStream withInstanceKey(final long instanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == instanceKey);
  }

  public ProcessInstanceCreationRecordStream withVariables(final Map<String, Object> variables) {
    return valueFilter(v -> v.getVariables().equals(variables));
  }

  public ProcessInstanceCreationRecordStream withVariables(
      final Map.Entry<String, Object>... entries) {
    return withVariables(Maps.of(entries));
  }

  public ProcessInstanceCreationRecordStream withVariables(
      final Predicate<Map<String, Object>> matcher) {
    return valueFilter(v -> matcher.test(v.getVariables()));
  }

  public ProcessInstanceCreationRecordStream withStartInstruction(final String elementId) {
    return valueFilter(
        v ->
            v.getStartInstructions().stream()
                .map(ProcessInstanceCreationStartInstructionValue::getElementId)
                .anyMatch(elementId::equals));
  }

  public ProcessInstanceCreationRecordStream limitToProcessInstanceCreated(
      final long processInstanceKey) {
    return limit(
        r ->
            r.getIntent() == ProcessInstanceCreationIntent.CREATED
                && r.getValue().getProcessInstanceKey() == processInstanceKey);
  }
}
