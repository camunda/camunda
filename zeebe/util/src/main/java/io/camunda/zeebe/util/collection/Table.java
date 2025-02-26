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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.Supplier;

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
public interface Table<RowT, ColT, T> {

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
  static <RowT, ColT, T> Table<RowT, ColT, T> simple() {
    return new MapTable<>();
  }

  static <RowT, ColT, T> Table<RowT, ColT, T> concurrent() {
    return new MapTable<>(ConcurrentHashMap::new);
  }

  /** Returns an optimized table for enum keys (both rows and columns). */
  static <RowT extends Enum<RowT>, ColT extends Enum<ColT>, T> Table<RowT, ColT, T> ofEnum(
      final Class<RowT> rowClass,
      final Class<ColT> columnClass,
      final IntFunction<T[]> arraySupplier) {
    final var marray =
        MArray.of(
            arraySupplier,
            rowClass.getEnumConstants().length,
            columnClass.getEnumConstants().length);
    return new EnumTable<>(marray);
  }

  /** A very simple implementation using nested maps. */
  @SuppressWarnings("unchecked")
  final class MapTable<RowT, ColT, T> implements Table<RowT, ColT, T> {
    private final Map<RowT, Map<ColT, T>> table;
    private final Supplier<Map<?, ?>> supplier;

    private MapTable() {
      this(HashMap::new);
    }

    /**
     * Construct the MapTable using this factory method for maps.
     *
     * @param supplier used to create an empty map
     */
    private MapTable(final Supplier<Map<?, ?>> supplier) {
      this.supplier = supplier;
      table = (Map<RowT, Map<ColT, T>>) supplier.get();
    }

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
      table.computeIfAbsent(rowKey, ignored -> (Map<ColT, T>) supplier.get()).put(columnKey, value);
    }

    @Override
    public T computeIfAbsent(
        final RowT rowKey, final ColT columnKey, final BiFunction<RowT, ColT, T> computer) {
      return table
          .computeIfAbsent(rowKey, ignored -> (Map<ColT, T>) supplier.get())
          .computeIfAbsent(columnKey, ignored -> computer.apply(rowKey, columnKey));
    }
  }

  /**
   * A table implementation optimized for {@link Enum} keys.
   *
   * <p>Enums allow us to define a finite keyspace, so we can efficiently use a single array to
   * represent all possible value combinations. The cardinality of the {@link RowT} enum represents
   * how many rows are in the table, and the cardinality of the {@link ColT} enum represents how
   * many columns.
   *
   * <p>The table indexes all possible combination of the {@link RowT} and {@link ColT} enums in a
   * single array whose size is equal to the cardinality of T times the cardinality of U. So given
   * two enums, one with 3 elements, and the other with 4, the array size would be 12.
   *
   * <p>The values in the table are stored in row-major order; that is, in rows where every element
   * on a row is stored contiguously. So the first row starts at index 0, and the second row starts
   * at the index equal to the cardinality of the second enum.
   */
  final class EnumTable<RowT extends Enum<RowT>, ColT extends Enum<ColT>, T>
      implements Table<RowT, ColT, T> {
    private final MArray<T> marray;

    private EnumTable(final MArray<T> marray) {
      if (marray.dimensions() != 2) {
        throw new IllegalArgumentException(
            "Expected a 2D m-array, but got %d dimensions".formatted(marray.dimensions()));
      }

      this.marray = marray;
    }

    @Override
    public T get(final RowT rowKey, final ColT columnKey) {
      return marray.get(rowKey.ordinal(), columnKey.ordinal());
    }

    @Override
    public void put(final RowT rowKey, final ColT columnKey, final T value) {
      marray.put(value, rowKey.ordinal(), columnKey.ordinal());
    }

    @Override
    public T computeIfAbsent(
        final RowT rowKey, final ColT columnKey, final BiFunction<RowT, ColT, T> computer) {
      final var value = marray.get(rowKey.ordinal(), columnKey.ordinal());
      if (value != null) {
        return value;
      }

      final var updated = computer.apply(rowKey, columnKey);
      marray.put(updated, rowKey.ordinal(), columnKey.ordinal());
      return updated;
    }
  }
}
