/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.read.domain.DbQueryPage.KeySetPaginationFieldEntry;
import io.camunda.db.rdbms.read.domain.DbQueryPage.Operator;
import io.camunda.search.sort.SortOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins the cursor comparator for every combination of seek direction, sort order and NULL-ness of
 * the cursor value.
 *
 * <p>The backward rows are the forward rows with the sort order swapped, because paging backwards
 * over a sort is paging forwards over the reversed sort. The comparators only look symmetric once
 * the standardized NULL placement (ASC NULLs FIRST, DESC NULLs LAST) is taken into account, so the
 * table is written out in full rather than derived.
 */
class KeySetPaginationFieldEntryTest {

  @ParameterizedTest(name = "[{index}] searchAfter={0}, {1}, cursor value {2} -> {3}")
  @CsvSource(
      nullValues = "NULL",
      value = {
        // forwards
        "true,  ASC,  cursor, GREATER",
        "true,  ASC,  NULL,   IS_NOT_NULL",
        "true,  DESC, cursor, LOWER",
        "true,  DESC, NULL,   NO_MATCH",
        // backwards: the forward rows with the order swapped
        "false, DESC, cursor, GREATER",
        "false, DESC, NULL,   IS_NOT_NULL",
        "false, ASC,  cursor, LOWER",
        "false, ASC,  NULL,   NO_MATCH",
      })
  void shouldPickComparatorForSeekDirection(
      final boolean isSearchAfter,
      final SortOrder order,
      final String fieldValue,
      final Operator expected) {
    // when
    final var entry =
        KeySetPaginationFieldEntry.of("CREATION_DATE", fieldValue)
            .order(order)
            .isSearchAfter(isSearchAfter)
            .build();

    // then
    assertThat(entry.operator()).isEqualTo(expected);
    assertThat(entry.fieldName()).isEqualTo("CREATION_DATE");
    assertThat(entry.fieldValue()).isEqualTo(fieldValue);
  }
}
