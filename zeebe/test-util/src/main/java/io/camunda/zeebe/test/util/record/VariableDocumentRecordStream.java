/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.collection.Maps;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class VariableDocumentRecordStream
    extends ExporterRecordStream<VariableDocumentRecordValue, VariableDocumentRecordStream> {

  public VariableDocumentRecordStream(
      final Stream<Record<VariableDocumentRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected VariableDocumentRecordStream supply(
      final Stream<Record<VariableDocumentRecordValue>> wrappedStream) {
    return new VariableDocumentRecordStream(wrappedStream);
  }

  public VariableDocumentRecordStream withScopeKey(final long scopeKey) {
    return valueFilter(v -> v.getScopeKey() == scopeKey);
  }

  public VariableDocumentRecordStream withUpdateSemantics(
      final VariableDocumentUpdateSemantic updateSemantics) {
    return valueFilter(v -> v.getUpdateSemantics() == updateSemantics);
  }

  public VariableDocumentRecordStream withVariables(final Map<String, Object> variables) {
    return valueFilter(v -> v.getVariables().equals(variables));
  }

  public VariableDocumentRecordStream withVariables(final Map.Entry<String, Object>... entries) {
    return withVariables(Maps.of(entries));
  }

  public VariableDocumentRecordStream withVariables(
      final Predicate<Map<String, Object>> variablesMatcher) {
    return valueFilter(v -> variablesMatcher.test(v.getVariables()));
  }
}
