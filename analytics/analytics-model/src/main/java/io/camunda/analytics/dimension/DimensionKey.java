/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A value-equal tuple of dimension values conforming to a {@link DimensionSchema} — the grouping
 * key the combiner buffers by and the durable rollup stores. Values are positional, matching the
 * schema's column order; a {@code null} value is the "unknown"/absent bucket for that dimension.
 * The generic replacement for the per-metric key records (e.g. {@code RegionKey}); the streaming
 * library only sees it as an opaque, value-equal key.
 */
public final class DimensionKey {

  private final DimensionSchema schema;
  private final List<Object> values;

  private DimensionKey(final DimensionSchema schema, final List<Object> values) {
    this.schema = schema;
    this.values = values;
  }

  public static DimensionKey of(final DimensionSchema schema, final Object... values) {
    return of(schema, Arrays.asList(values));
  }

  public static DimensionKey of(final DimensionSchema schema, final List<?> values) {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(values, "values");
    if (values.size() != schema.size()) {
      throw new IllegalArgumentException(
          "expected " + schema.size() + " values for " + schema + " but got " + values.size());
    }
    final List<Object> copy = new ArrayList<>(values.size());
    for (int i = 0; i < values.size(); i++) {
      final Object value = values.get(i);
      validate(schema.column(i), value);
      copy.add(value);
    }
    return new DimensionKey(schema, Collections.unmodifiableList(copy));
  }

  private static void validate(final DimensionColumn column, final Object value) {
    if (value == null) {
      return;
    }
    final boolean ok =
        switch (column.type()) {
          case STRING, TEXT -> value instanceof String;
          case LONG -> value instanceof Long;
          case INT -> value instanceof Integer;
          case BOOLEAN -> value instanceof Boolean;
        };
    if (!ok) {
      throw new IllegalArgumentException(
          "value "
              + value
              + " ("
              + value.getClass().getSimpleName()
              + ") is not "
              + column.type()
              + " for dimension '"
              + column.name()
              + "'");
    }
  }

  public DimensionSchema schema() {
    return schema;
  }

  public List<Object> values() {
    return values;
  }

  public Object get(final int index) {
    return values.get(index);
  }

  /** The value for the named dimension; throws if this key's schema has no such column. */
  public Object get(final String name) {
    final int index = schema.indexOf(name);
    if (index < 0) {
      throw new IllegalArgumentException("no dimension '" + name + "' in " + schema);
    }
    return values.get(index);
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final DimensionKey other
        && schema.equals(other.schema)
        && values.equals(other.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(schema, values);
  }

  @Override
  public String toString() {
    return "DimensionKey" + values;
  }
}
