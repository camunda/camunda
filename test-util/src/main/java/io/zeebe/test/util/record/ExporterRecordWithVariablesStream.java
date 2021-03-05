/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValueWithVariables;
import java.util.Map;
import java.util.stream.Stream;

public abstract class ExporterRecordWithVariablesStream<
        T extends RecordValueWithVariables, S extends ExporterRecordWithVariablesStream<T, S>>
    extends ExporterRecordStream<T, S> {

  public ExporterRecordWithVariablesStream(final Stream<Record<T>> wrappedStream) {
    super(wrappedStream);
  }

  public S withVariables(final Map<String, Object> variables) {
    return valueFilter(v -> variables.equals(v.getVariables()));
  }

  public S withVariablesContaining(final String key) {
    return valueFilter(v -> v.getVariables().containsKey(key));
  }

  public S withVariablesContaining(final String key, final Object value) {
    return valueFilter(v -> value.equals(v.getVariables().get(key)));
  }
}
