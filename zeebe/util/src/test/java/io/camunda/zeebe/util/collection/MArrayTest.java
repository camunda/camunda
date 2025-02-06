/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class MArrayTest {
  @ParameterizedTest
  @MethodSource("provideDimensions")
  void shouldNotOverwriteValues(final int[] dimensions) {
    // given
    final var marray = MArray.of(String[]::new, dimensions);

    // when
    fill(marray, 0, dimensions, new int[0]);

    // then
    assertMArray(marray, 0, dimensions, new int[0]);
  }

  private static Stream<Named<int[]>> provideDimensions() {
    return Stream.of(
        Named.of("2D", new int[] {2, 3}),
        Named.of("3D", new int[] {3, 2, 2}),
        Named.of("4D", new int[] {2, 3, 2, 3}));
  }

  private void fill(
      final MArray<String> marray,
      final int dimensionOffset,
      final int[] dimensions,
      final int[] indices) {
    if (dimensionOffset == dimensions.length) {
      final var value = indicesAsStringValue(indices);
      marray.put(value, indices);
      return;
    }

    final var dimension = dimensions[dimensionOffset];
    for (int i = 0; i < dimension; i++) {
      final var newIndices = Arrays.copyOf(indices, indices.length + 1);
      newIndices[newIndices.length - 1] = i;
      fill(marray, dimensionOffset + 1, dimensions, newIndices);
    }
  }

  private void assertMArray(
      final MArray<String> marray,
      final int dimensionOffset,
      final int[] dimensions,
      final int[] indices) {
    if (dimensionOffset == dimensions.length) {
      final var value = indicesAsStringValue(indices);
      assertThat(marray.get(indices))
          .as("value at index %s == %s", Arrays.toString(indices), value)
          .isEqualTo(value);
      return;
    }

    final var dimension = dimensions[dimensionOffset];
    for (int i = 0; i < dimension; i++) {
      final var newIndices = Arrays.copyOf(indices, indices.length + 1);
      newIndices[newIndices.length - 1] = i;
      assertMArray(marray, dimensionOffset + 1, dimensions, newIndices);
    }
  }

  private String indicesAsStringValue(final int[] indices) {
    return Arrays.stream(indices).mapToObj(String::valueOf).collect(Collectors.joining());
  }
}
