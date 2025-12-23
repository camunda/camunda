/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.transformers.SearchTransfomer;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.FieldValue;

public class AggregationCursorTransformer
    implements SearchTransfomer<Object[], Map<String, FieldValue>> {

  @Override
  public Map<String, FieldValue> apply(final Object[] value) {
    return Stream.of(value)
        .filter(item -> item instanceof Map<?, ?>)
        .flatMap(item -> ((Map<?, ?>) item).entrySet().stream())
        .collect(
            Collectors.toMap(
                entry -> entry.getKey().toString(), entry -> toFieldValue(entry.getValue())));
  }

  public static FieldValue toFieldValue(final Object value) {
    if (value == null) {
      return FieldValue.NULL;
    } else if (String.class.isAssignableFrom(value.getClass())) {
      return FieldValue.of((String) value);
    } else if (Integer.class.isAssignableFrom(value.getClass())) {
      return FieldValue.of((Integer) value);
    } else if (Long.class.isAssignableFrom(value.getClass())) {
      return FieldValue.of((Long) value);
    } else if (Double.class.isAssignableFrom(value.getClass())) {
      return FieldValue.of((Double) value);
    } else if (Float.class.isAssignableFrom(value.getClass())) {
      return FieldValue.of((Float) value);
    } else if (Boolean.class.isAssignableFrom(value.getClass())) {
      return FieldValue.of((Boolean) value);
    }
    throw new IllegalArgumentException("Non-supported type: " + value.getClass());
  }
}
