/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.stream.Stream;

public final class VariableRecordStream
    extends ExporterRecordStream<VariableRecordValue, VariableRecordStream> {

  public VariableRecordStream(final Stream<Record<VariableRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected VariableRecordStream supply(final Stream<Record<VariableRecordValue>> wrappedStream) {
    return new VariableRecordStream(wrappedStream);
  }

  public VariableRecordStream withName(final String name) {
    return valueFilter(v -> v.getName().equals(name));
  }

  public VariableRecordStream withScopeKey(final long scopeKey) {
    return valueFilter(v -> v.getScopeKey() == scopeKey);
  }

  public VariableRecordStream withValue(final String value) {
    return valueFilter(v -> v.getValue().equals(value));
  }

  public VariableRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  /** @return only the variables that are created in the process instance scope (i.e. root scope) */
  public VariableRecordStream filterProcessInstanceScope() {
    return valueFilter(v -> v.getScopeKey() == v.getProcessInstanceKey());
  }
}
