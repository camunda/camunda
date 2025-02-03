/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TableTest {
  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldReturnNullOnMissingColumn(final Table<Row, Column, Integer> table) {
    // given
    table.put(Row.A, Column.D, 1);

    // when
    final var value = table.get(Row.A, Column.E);

    // then
    assertThat(value).isNull();
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldReturnNullOnMissingRow(final Table<Row, Column, Integer> table) {
    // given
    table.put(Row.A, Column.D, 1);

    // when
    final var value = table.get(Row.B, Column.E);

    // then
    assertThat(value).isNull();
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldReturnLatestValue(final Table<Row, Column, Integer> table) {
    // given
    table.put(Row.A, Column.D, 1);
    table.put(Row.A, Column.D, 2);

    // when
    final var value = table.get(Row.A, Column.D);

    // then
    assertThat(value).isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldNotModifyOtherValues(final Table<Row, Column, Integer> table) {
    // given
    table.put(Row.A, Column.D, 1);
    table.put(Row.A, Column.E, 2);
    table.put(Row.B, Column.D, 3);

    // when
    table.put(Row.A, Column.D, 2);

    // then
    assertThat(table.get(Row.A, Column.D)).isEqualTo(2);
    assertThat(table.get(Row.A, Column.E)).isEqualTo(2);
    assertThat(table.get(Row.B, Column.D)).isEqualTo(3);
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldComputeOnlyIfAbsent(final Table<Row, Column, Integer> table) {
    // given
    table.put(Row.A, Column.D, 1);

    // when
    final var value = table.computeIfAbsent(Row.A, Column.D, (r, c) -> 2);

    // then
    assertThat(value).isOne();
    assertThat(table.get(Row.A, Column.D)).isOne();
  }

  @ParameterizedTest
  @MethodSource("provideImplementations")
  void shouldComputeIfAbsent(final Table<Row, Column, Integer> table) {
    // given
    table.put(Row.A, Column.D, 1);

    // when
    final var value = table.computeIfAbsent(Row.A, Column.E, (r, c) -> 2);

    // then
    assertThat(value).isEqualTo(2);
    assertThat(table.get(Row.A, Column.E)).isEqualTo(2);
  }

  private static Stream<Named<Table<Row, Column, Integer>>> provideImplementations() {
    return Stream.of(
        Named.named("EnumTable", Table.ofEnum(Row.class, Column.class, Integer[]::new)),
        Named.named("MapTable", Table.simple()));
  }

  private enum Row {
    A,
    B,
    C
  }

  private enum Column {
    D,
    E,
    F
  }
}
