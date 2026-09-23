/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import java.util.stream.Stream;

public final class ManagedScriptDefinitionRecordStream
    extends ExporterRecordStream<
        ManagedScriptDefinitionRecordValue, ManagedScriptDefinitionRecordStream> {

  public ManagedScriptDefinitionRecordStream(
      final Stream<Record<ManagedScriptDefinitionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ManagedScriptDefinitionRecordStream supply(
      final Stream<Record<ManagedScriptDefinitionRecordValue>> wrappedStream) {
    return new ManagedScriptDefinitionRecordStream(wrappedStream);
  }

  public ManagedScriptDefinitionRecordStream withProcessDefinitionKey(
      final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public ManagedScriptDefinitionRecordStream withManagedScriptDefinitionKey(
      final long managedScriptDefinitionKey) {
    return valueFilter(v -> v.getManagedScriptDefinitionKey() == managedScriptDefinitionKey);
  }

  public ManagedScriptDefinitionRecordStream withStatus(
      final ManagedScriptDefinitionStatus status) {
    return valueFilter(v -> v.getStatus() == status);
  }

  public ManagedScriptDefinitionRecordStream withElementId(final String elementId) {
    return valueFilter(v -> elementId.equals(v.getElementId()));
  }

  public ManagedScriptDefinitionRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }
}
