/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.FieldValue;
import io.camunda.search.clients.transformers.SearchTransfomer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class AggregationCursorTransformer
    implements SearchTransfomer<Object[], Map<String, FieldValue>> {

  @Override
  public Map<String, FieldValue> apply(final Object[] value) {
    final Map<String, FieldValue> cursorValues = new HashMap<>();
    Stream.of(value)
        .filter(item -> item instanceof Map<?, ?>)
        .flatMap(item -> ((Map<?, ?>) item).entrySet().stream())
        // a null after-value (a composite missing_bucket source) must stay a typed null
        .forEach(
            entry ->
                cursorValues.put(
                    entry.getKey().toString(),
                    entry.getValue() == null ? FieldValue.NULL : FieldValue.of(entry.getValue())));
    return cursorValues;
  }
}
