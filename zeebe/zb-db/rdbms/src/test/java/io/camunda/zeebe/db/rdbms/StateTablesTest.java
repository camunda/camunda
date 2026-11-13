/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class StateTablesTest {

  @Test
  void shouldGiveEveryLiveColumnFamilyATableOfItsOwn() {
    // given
    final var live = columnFamilies().filter(columnFamily -> !isDeprecated(columnFamily));

    // when / then
    assertThat(live)
        .allSatisfy(
            columnFamily ->
                assertThat(StateTables.of(columnFamily.getValue()).shared())
                    .describedAs(
                        "Expected %s to have a table of its own; give it a short name in StateTables",
                        columnFamily)
                    .isFalse());
  }

  @Test
  void shouldKeepDeprecatedColumnFamiliesInTheSharedTable() {
    // given
    final var deprecated = columnFamilies().filter(StateTablesTest::isDeprecated);

    // when / then
    assertThat(deprecated)
        .isNotEmpty()
        .allSatisfy(
            columnFamily ->
                assertThat(StateTables.of(columnFamily.getValue())).isEqualTo(StateTables.OTHER));
  }

  @Test
  void shouldKeepUnknownColumnFamiliesInTheSharedTable() {
    // given
    final int unused =
        IntStream.iterate(0, number -> number + 1)
            .filter(number -> columnFamilies().noneMatch(cf -> cf.getValue() == number))
            .findFirst()
            .orElseThrow();

    // when / then
    assertThat(StateTables.of(unused)).isEqualTo(StateTables.OTHER);
    assertThat(StateTables.of(10_000)).isEqualTo(StateTables.OTHER);
    assertThat(StateTables.of(-1)).isEqualTo(StateTables.OTHER);
  }

  @Test
  void shouldKeepNamesShortEnoughForEveryDatabase() {
    // when
    final var names = StateTables.all().stream().map(StateTable::name);

    // then
    assertThat(names)
        .allSatisfy(
            name ->
                assertThat(name)
                    .startsWith("ZS_")
                    .hasSizeLessThanOrEqualTo(3 + StateTables.MAX_SHORT_NAME_LENGTH));
  }

  @Test
  void shouldGiveEveryTableADistinctName() {
    // when
    final var names =
        Stream.concat(
            Stream.of(StateTables.PARTITION), StateTables.all().stream().map(StateTable::name));

    // then
    assertThat(names).doesNotHaveDuplicates();
  }

  @Test
  void shouldGiveEveryViewColumnAPlainDistinctName() {
    // given
    final var fixed = List.of("PARTITION_ID", "UPDATED_POSITION", "STATE_KEY", "STATE_VALUE");
    final var layouts =
        StateTables.all().stream().map(StateTable::keyLayout).filter(Objects::nonNull);

    // when / then
    assertThat(layouts)
        .isNotEmpty()
        .allSatisfy(
            layout -> {
              final var columns = layout.parts().stream().map(KeyLayout.Part::column);
              assertThat(columns)
                  .doesNotHaveDuplicates()
                  .doesNotContainAnyElementsOf(fixed)
                  .allSatisfy(column -> assertThat(column).matches("[A-Z][A-Z0-9_]{0,29}"));
            });
  }

  private static Stream<ZbColumnFamilies> columnFamilies() {
    return Arrays.stream(ZbColumnFamilies.values());
  }

  private static boolean isDeprecated(final ZbColumnFamilies columnFamily) {
    try {
      return ZbColumnFamilies.class
          .getField(columnFamily.name())
          .isAnnotationPresent(Deprecated.class);
    } catch (final NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }
}
