/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.collection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/** A very simple implementation using a map-of-map approach. */
final class MapTable<RowT, ColT, T> implements Table<RowT, ColT, T> {
  private final Map<RowT, Map<ColT, T>> table = new HashMap<>();

  @Override
  public T get(final RowT rowKey, final ColT columnKey) {
    final var row = table.get(rowKey);
    if (row == null) {
      return null;
    }

    return row.get(columnKey);
  }

  @Override
  public void put(final RowT rowKey, final ColT columnKey, final T value) {
    table.computeIfAbsent(rowKey, ignored -> new HashMap<>()).put(columnKey, value);
  }

  @Override
  public T computeIfAbsent(
      final RowT rowKey, final ColT columnKey, final BiFunction<RowT, ColT, T> computer) {
    return table
        .computeIfAbsent(rowKey, ignored -> new HashMap<>())
        .computeIfAbsent(columnKey, ignored -> computer.apply(rowKey, columnKey));
  }
}
