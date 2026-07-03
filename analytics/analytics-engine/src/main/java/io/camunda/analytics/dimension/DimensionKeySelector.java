/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import io.camunda.eventbridge.streaming.aggregate.KeySelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the {@link DimensionKey} a fact groups under by reading each column of a {@link
 * DimensionSchema} from the fact's {@link FactRow} by name. This is the generic replacement for the
 * per-metric {@code KeySelector} lambdas (e.g. {@code Metrics::definitionKey}) — the grain is data
 * (the schema), not code.
 *
 * <p>Deterministic, as {@link KeySelector} requires: a fact always yields the same key. A dimension
 * absent from the row is {@code null} (the "unknown" bucket). Narrowing numeric coercions ({@code
 * Integer}/{@code Long} → the column's declared type) are applied so a fact that exposes, say, an
 * {@code int} version for a {@code LONG} column still binds; anything else defers to {@link
 * DimensionKey}'s type validation.
 */
public final class DimensionKeySelector implements KeySelector<FactRow, DimensionKey> {

  private final DimensionSchema schema;

  public DimensionKeySelector(final DimensionSchema schema) {
    this.schema = Objects.requireNonNull(schema, "schema");
  }

  public DimensionSchema schema() {
    return schema;
  }

  @Override
  public DimensionKey getKey(final FactRow fact) {
    Objects.requireNonNull(fact, "fact");
    final List<Object> values = new ArrayList<>(schema.size());
    for (final DimensionColumn column : schema.columns()) {
      values.add(coerce(column, fact.get(column.name())));
    }
    return DimensionKey.of(schema, values);
  }

  private static Object coerce(final DimensionColumn column, final Object value) {
    if (!(value instanceof final Number number)) {
      return value;
    }
    return switch (column.type()) {
      case LONG -> number.longValue();
      case INT -> number.intValue();
      default -> value;
    };
  }
}
