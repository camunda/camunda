/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class Map3DTest {
  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldReturnNullOnMissingColumn(final Map3D<Row, Column, Face, Integer> map) {
    // given
    map.put(Row.A, Column.D, Face.F, 1);

    // when
    final var value = map.get(Row.A, Column.E, Face.F);

    // then
    assertThat(value).isNull();
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldReturnNullOnMissingRow(final Map3D<Row, Column, Face, Integer> map) {
    // given
    map.put(Row.A, Column.D, Face.G, 1);

    // when
    final var value = map.get(Row.B, Column.D, Face.G);

    // then
    assertThat(value).isNull();
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldReturnNullOnMissingFace(final Map3D<Row, Column, Face, Integer> map) {
    // given
    map.put(Row.A, Column.D, Face.G, 1);

    // when
    final var value = map.get(Row.A, Column.D, Face.F);

    // then
    assertThat(value).isNull();
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldReturnLatestValue(final Map3D<Row, Column, Face, Integer> map) {
    // given
    map.put(Row.A, Column.D, Face.F, 1);
    map.put(Row.A, Column.D, Face.F, 2);

    // when
    final var value = map.get(Row.A, Column.D, Face.F);

    // then
    assertThat(value).isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldNotModifyOtherValues(final Map3D<Row, Column, Face, Integer> map) {
    // given
    map.put(Row.A, Column.D, Face.F, 1);
    map.put(Row.A, Column.E, Face.F, 2);
    map.put(Row.B, Column.D, Face.G, 3);

    // when
    map.put(Row.A, Column.D, Face.F, 2);

    // then
    assertThat(map.get(Row.A, Column.D, Face.F)).isEqualTo(2);
    assertThat(map.get(Row.A, Column.E, Face.F)).isEqualTo(2);
    assertThat(map.get(Row.B, Column.D, Face.G)).isEqualTo(3);
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldComputeOnlyIfAbsent(final Map3D<Row, Column, Face, Integer> map) {
    // given
    map.put(Row.A, Column.D, Face.F, 1);

    // when
    final var value = map.computeIfAbsent(Row.A, Column.D, Face.F, (r, c, f) -> 2);

    // then
    assertThat(value).isOne();
    assertThat(map.get(Row.A, Column.D, Face.F)).isOne();
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldComputeIfAbsent(final Map3D<Row, Column, Face, Integer> map) {
    // given
    map.put(Row.A, Column.D, Face.F, 1);

    // when
    final var value = map.computeIfAbsent(Row.A, Column.E, Face.F, (r, c, f) -> 2);

    // then
    assertThat(value).isEqualTo(2);
    assertThat(map.get(Row.A, Column.E, Face.F)).isEqualTo(2);
  }

  private static Stream<Named<Map3D<Row, Column, Face, Integer>>> provideImplementations() {
    return Stream.of(
        Named.named("EnumCube", Map3D.ofEnum(Row.class, Column.class, Face.class, Integer[]::new)),
        Named.named("MapCube", Map3D.simple()));
  }

  private enum Row {
    A,
    B,
    C
  }

  private enum Column {
    D,
    E
  }

  private enum Face {
    F,
    G,
    H
  }
}
