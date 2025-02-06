/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.collection;

import java.util.Arrays;
import java.util.function.IntFunction;

/**
 * An m-array is a multidimensional array, where each dimension has a finite bound, and represented
 * by a single underlying array. As such, it is poorly optimized for sparse collections, but is very
 * efficient if you expect most of it to be filled.
 *
 * @param <T> the type of the value stored
 */
public final class MArray<T> {
  private final int[] offsets;
  private final T[] items;

  private MArray(final int[] offsets, final T[] items) {
    this.offsets = offsets;
    this.items = items;
  }

  /**
   * Returns a new multidimensional array based on the given cardinalities.
   *
   * @param arraySupplier a function which takes in the maximum number of items this array can hold,
   *     and returns a base array of that size
   * @param cardinalities the expected cardinalities per dimension
   * @return a new, pre-allocated multidimensional array
   * @param <T> the expected type of the values stored
   */
  public static <T> MArray<T> of(final IntFunction<T[]> arraySupplier, final int... cardinalities) {
    // as an optimization, we pre-compute the offset for each dimension as the product of all
    // cardinalities after that dimension; meaning for 0, it's the product of all cardinalities [1,
    // inf[, for 1 it's [2, inf[, etc.
    // this lets us very quickly compute the index of an element in the underlying array based on
    // the given index per dimension
    final int[] offsets = new int[cardinalities.length - 1];
    int size = cardinalities[0];
    for (int i = 1; i < cardinalities.length; i++) {
      offsets[i - 1] =
          Arrays.stream(cardinalities, i, cardinalities.length).reduce(1, (a, b) -> a * b);
      size *= cardinalities[i];
    }

    return new MArray<>(offsets, arraySupplier.apply(size));
  }

  /** Returns the number of dimensions of this array */
  public int dimensions() {
    return offsets.length + 1;
  }

  /**
   * Returns the value stored at the given vector, or null.
   *
   * @throws IllegalArgumentException if the index vector is smaller than the number of dimensions
   */
  public T get(final int... indices) {
    final var index = mapToIndex(indices);
    return items[index];
  }

  /**
   * Sets the value at the given vector.
   *
   * @throws IllegalArgumentException if the index vector is smaller than the number of dimensions
   */
  public void put(final T value, final int... indices) {
    final var index = mapToIndex(indices);
    items[index] = value;
  }

  private int mapToIndex(final int... indices) {
    if (indices.length != offsets.length + 1) {
      throw new IllegalArgumentException(
          "Expected to compute the index in a multi-dimensional array with %d dimensions, but received %d dimensions"
              .formatted(offsets.length + 1, indices.length));
    }

    int index = 0;
    for (int i = 0; i < indices.length - 1; i++) {
      index += indices[i] * offsets[i];
    }

    return index + indices[indices.length - 1];
  }
}
