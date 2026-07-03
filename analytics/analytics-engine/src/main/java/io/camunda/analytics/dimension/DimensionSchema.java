/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import java.util.List;
import java.util.Objects;

/**
 * The ordered, typed grain of a rollup cube: the dimension columns a {@link DimensionKey} carries,
 * in a fixed order. Order is significant and stable — new dimensions are appended, never reordered,
 * so previously written keys stay decodable (a later, forward-only concern). A purely domain
 * concept; the streaming library only ever sees the derived {@link DimensionKey} as an opaque,
 * value-equal key. Generalises the per-metric key records (e.g. {@code RegionKey}) into one schema.
 */
public final class DimensionSchema {

  private final List<DimensionColumn> columns;

  private DimensionSchema(final List<DimensionColumn> columns) {
    this.columns = columns;
  }

  public static DimensionSchema of(final DimensionColumn... columns) {
    return of(List.of(columns));
  }

  public static DimensionSchema of(final List<DimensionColumn> columns) {
    Objects.requireNonNull(columns, "columns");
    final List<DimensionColumn> copy = List.copyOf(columns);
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("a dimension schema must have at least one column");
    }
    final long distinctNames = copy.stream().map(DimensionColumn::name).distinct().count();
    if (distinctNames != copy.size()) {
      throw new IllegalArgumentException("dimension column names must be unique: " + copy);
    }
    return new DimensionSchema(copy);
  }

  public List<DimensionColumn> columns() {
    return columns;
  }

  public int size() {
    return columns.size();
  }

  public DimensionColumn column(final int index) {
    return columns.get(index);
  }

  public DimensionType type(final int index) {
    return columns.get(index).type();
  }

  /** The position of {@code name} in the grain, or {@code -1} if this schema has no such column. */
  public int indexOf(final String name) {
    for (int i = 0; i < columns.size(); i++) {
      if (columns.get(i).name().equals(name)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final DimensionSchema other && columns.equals(other.columns);
  }

  @Override
  public int hashCode() {
    return columns.hashCode();
  }

  @Override
  public String toString() {
    return "DimensionSchema" + columns;
  }
}
