/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.collection;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * A table is a data structure which maps rows and columns to a single value. Think of it like your
 * usual {@link Map}, except your key is made of two parts.
 *
 * <p>If we map our table visually as a matrix (with dummy values), it looks like:
 *
 * <pre>
 *     | A | B | C
 * FOO | 1 | 2 | 3
 * BAR | 4 | 5 | 6
 * </pre>
 *
 * NOTE: there exists an equivalent concept in Guava. As we only need very, very limited
 * capabilities at the moment, it's fine to have our own. If this grows quite a bit, we can evaluate
 * using Guava's directly.
 *
 * <p>If your row and column types are enums, use {@link EnumTable}.
 *
 * @param <T> the type for the actual values stored
 * @param <RowT> the type of the row keys
 * @param <ColT> the type of the column keys
 */
public interface Table<T, RowT, ColT> {

  /**
   * Returns the value indexed by the two given key parts. If none found, returns null.
   *
   * @param rowKey the key of the row to look into
   * @param columnKey which column in the row to return
   * @return the value indexed at the row/column, or null if none
   */
  T get(RowT rowKey, ColT columnKey);

  /**
   * Sets the given value at the column {@code columnKey} in row {@code rowKey}.
   *
   * @param rowKey the key to select the row with
   * @param columnKey the key to select the column in the row with
   * @param value the value to store
   */
  void put(RowT rowKey, ColT columnKey, T value);

  /**
   * Returns the current value at the given column in the given row. If there is none, sets it to
   * whatever the given supplier returns, and returns that immediately.
   *
   * @param rowKey the key of the row to look into
   * @param columnKey which column in the row to return
   * @param computer a function which should compute the value for this row/column pair
   * @return the value indexed at the row/column, or null if none
   */
  T computeIfAbsent(RowT rowKey, ColT columnKey, BiFunction<RowT, ColT, T> computer);

  /**
   * Returns a basic implementation of a table. If using enums for row and column keys, use {@link
   * #ofEnum(Class, Class, IntFunction)} instead.
   */
  static <T, RowT, ColT> Table<T, RowT, ColT> simple() {
    return new MapTable<>();
  }

  /** Returns an optimized table for enum keys (both rows and columns). */
  static <T, RowT extends Enum<RowT>, ColT extends Enum<ColT>> Table<T, RowT, ColT> ofEnum(
      final Class<RowT> rowClass,
      final Class<ColT> columnClass,
      final IntFunction<T[]> arraySupplier) {
    return new EnumTable<>(rowClass, columnClass, arraySupplier);
  }
}
