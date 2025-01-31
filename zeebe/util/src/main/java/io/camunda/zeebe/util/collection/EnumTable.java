/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.collection;

import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * A table implementation optimized for {@link Enum} keys. See {@link MapTable} for more on tables.
 *
 * <p>Enums allow us to define a finite keyspace, so we can efficiently use a single array to
 * represent all possible value combinations. The cardinality of the {@link RowT} enum represents
 * how many rows are in the table, and the cardinality of the {@link ColT} enum represents how many
 * columns.
 *
 * <p>The table indexes all possible combination of the {@link RowT} and {@link ColT} enums in a
 * single array whose size is equal to the cardinality of T times the cardinality of U. So given two
 * enums, one with 3 elements, and the other with 4, the array size would be 12.
 *
 * <p>The values in the table are stored in row-major order; that is, in rows where every element on
 * a row is stored contiguously. So the first row starts at index 0, and the second row starts at
 * the index equal to the cardinality of the second enum.
 *
 * <p>Let's use an example. Assume we have the following enums:
 *
 * <pre>{@code
 * enum T {
 *   FOO, BAR
 * }
 * enum U {
 *   A, B, C
 * }
 * }</pre>
 *
 * In this case, we will have an array of size 6, with 2 rows, and 3 columns, meaning each row
 * contains 3 elements. If we map our table visually as a matrix (with dummy values), it looks like:
 *
 * <pre>
 *     | A | B | C
 * FOO | 1 | 2 | 3
 * BAR | 4 | 5 | 6
 * </pre>
 *
 * Let FOO_A denote the value at row FOO and column A (i.e. 1). If we map this as an array, we have:
 *
 * <pre>
 * [ FOO_A, FOO_B, FOO_C, BAR_A, BAR_B, BAR_C ]
 * </pre>
 */
final class EnumTable<RowT extends Enum<RowT>, ColT extends Enum<ColT>, T>
    implements Table<RowT, ColT, T> {
  private final T[] items;
  private final int columnCount;

  EnumTable(
      final Class<RowT> rowClass,
      final Class<ColT> columnClass,
      final IntFunction<T[]> arraySupplier) {
    final int rowCount = rowClass.getEnumConstants().length;
    columnCount = columnClass.getEnumConstants().length;
    items = arraySupplier.apply(rowCount * columnCount);
  }

  @Override
  public T get(final RowT rowKey, final ColT columnKey) {
    final var index = mapToIndex(rowKey, columnKey);
    return items[index];
  }

  @Override
  public void put(final RowT rowKey, final ColT columnKey, final T value) {
    final var index = mapToIndex(rowKey, columnKey);
    items[index] = value;
  }

  @Override
  public T computeIfAbsent(
      final RowT rowKey, final ColT columnKey, final BiFunction<RowT, ColT, T> computer) {
    final var index = mapToIndex(rowKey, columnKey);
    final var value = items[index];
    if (value != null) {
      return value;
    }

    final var updated = computer.apply(rowKey, columnKey);
    items[index] = updated;
    return updated;
  }

  private int mapToIndex(final RowT firstPart, final ColT secondPart) {
    return firstPart.ordinal() * columnCount + secondPart.ordinal();
  }
}
