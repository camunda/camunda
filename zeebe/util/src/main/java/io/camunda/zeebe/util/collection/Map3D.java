/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.collection;

import io.camunda.zeebe.util.function.TriFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A cuboid is a 3D matrix, also sometimes known as a prism, or 3D tensor. It maps 3 different keys
 * to a single value. You can visualize it as a triple of tables:
 *
 * <pre>
 *      t000, t010, t020     t001, t111, t121     t002, t012, t022
 * T = (t100, t110, t120),  (t101, t111, t121),  (t102, t112, t222)
 *      t200, t210, t220     t201, t211, t221     t202, t212, t222
 * </pre>
 *
 * In a way, it's a "decomposed" cube, with each table above representing a 2D plane, or a "face" of
 * the cube.
 *
 * <p>If your row and column types are enums, use {@link #ofEnum(Class, Class, Class, IntFunction)}.
 *
 * @param <T> the type for the actual values stored
 * @param <RowT> the type of the row keys
 * @param <ColT> the type of the column keys
 * @param <FaceT> the type of the face keys
 */
public interface Map3D<RowT, ColT, FaceT, T> {

  /**
   * Returns the value indexed by the two given key parts. If none found, returns null.
   *
   * @param rowKey the key of the row to look into
   * @param columnKey which column in the row to return
   * @param faceKey which face of the cube to pick the row/column pair from
   * @return the value indexed at the row/column, or null if none
   */
  T get(final RowT rowKey, final ColT columnKey, final FaceT faceKey);

  /**
   * Sets the given value at the column {@code columnKey} in row {@code rowKey}.
   *
   * @param rowKey the key to select the row with
   * @param columnKey the key to select the column in the row with
   * @param faceKey which face of the cube to pick the row/column pair from
   * @param value the value to store
   */
  void put(final RowT rowKey, final ColT columnKey, final FaceT faceKey, T value);

  /**
   * Returns the current value at the given column in the given row. If there is none, sets it to
   * whatever the given supplier returns, and returns that immediately.
   *
   * @param rowKey the key of the row to look into
   * @param columnKey which column in the row to return
   * @param faceKey which face of the cube to pick the row/column pair from
   * @param computer a function which should compute the value for this row/column pair
   * @return the value indexed at the row/column, or null if none
   */
  T computeIfAbsent(
      final RowT rowKey,
      final ColT columnKey,
      final FaceT faceKey,
      final TriFunction<RowT, ColT, FaceT, T> computer);

  /**
   * Returns a basic implementation of a table. If using bounded types for rows, columns, and face
   * keys (e.g. enums), use {@link #ofEnum(Class, Class, Class, IntFunction)} instead.
   */
  static <RowT, ColT, FaceT, T> Map3D<RowT, ColT, FaceT, T> simple() {
    return new HashMap3D<>();
  }

  /**
   * Returns an implementation optimized for rows/columns/faces with bounds, using enum for keys.
   */
  static <RowT extends Enum<RowT>, ColT extends Enum<ColT>, FaceT extends Enum<FaceT>, T>
      Map3D<RowT, ColT, FaceT, T> ofEnum(
          final Class<RowT> rowClass,
          final Class<ColT> columnClass,
          final Class<FaceT> faceClass,
          final IntFunction<T[]> arraySupplier) {
    final var marray =
        MArray.of(
            arraySupplier,
            rowClass.getEnumConstants().length,
            columnClass.getEnumConstants().length,
            faceClass.getEnumConstants().length);
    return new EnumMap3D<>(marray);
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
  final class EnumMap3D<
          RowT extends Enum<RowT>, ColT extends Enum<ColT>, FaceT extends Enum<FaceT>, T>
      implements Map3D<RowT, ColT, FaceT, T> {
    private final MArray<T> marray;

    private EnumMap3D(final MArray<T> marray) {
      this.marray = marray;
    }

    @Override
    public T get(final RowT rowKey, final ColT columnKey, final FaceT faceKey) {
      return marray.get(rowKey.ordinal(), columnKey.ordinal(), faceKey.ordinal());
    }

    @Override
    public void put(final RowT rowKey, final ColT columnKey, final FaceT faceKey, final T value) {
      marray.put(value, rowKey.ordinal(), columnKey.ordinal(), faceKey.ordinal());
    }

    @Override
    public T computeIfAbsent(
        final RowT rowKey,
        final ColT columnKey,
        final FaceT faceKey,
        final TriFunction<RowT, ColT, FaceT, T> computer) {
      final var value = get(rowKey, columnKey, faceKey);
      if (value != null) {
        return value;
      }

      final var updated = computer.apply(rowKey, columnKey, faceKey);
      put(rowKey, columnKey, faceKey, updated);
      return updated;
    }
  }

  /** A very simple implementation using a nested maps. */
  final class HashMap3D<RowT, ColT, FaceT, T> implements Map3D<RowT, ColT, FaceT, T> {
    private final Map<RowT, Map<ColT, Map<FaceT, T>>> cube = new HashMap<>();

    @Override
    public T get(final RowT rowKey, final ColT columnKey, final FaceT faceKey) {
      final var row = cube.get(rowKey);
      if (row == null) {
        return null;
      }

      final var column = row.get(columnKey);
      return column == null ? null : column.get(faceKey);
    }

    @Override
    public void put(final RowT rowKey, final ColT columnKey, final FaceT faceKey, final T value) {
      cube.computeIfAbsent(rowKey, ignored -> new HashMap<>())
          .computeIfAbsent(columnKey, ignored -> new HashMap<>())
          .put(faceKey, value);
    }

    @Override
    public T computeIfAbsent(
        final RowT rowKey,
        final ColT columnKey,
        final FaceT faceKey,
        final TriFunction<RowT, ColT, FaceT, T> computer) {
      return cube.computeIfAbsent(rowKey, ignored -> new HashMap<>())
          .computeIfAbsent(columnKey, ignored -> new HashMap<>())
          .computeIfAbsent(faceKey, ignored -> computer.apply(rowKey, columnKey, faceKey));
    }
  }
}
