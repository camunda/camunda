/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.sink.ColumnType;
import org.junit.jupiter.api.Test;

class HeapDictColumnTest {

  @Test
  void shouldRoundTripValuesThroughTheSharedInterner() {
    // given
    final Interner interner = new Interner();
    final HeapDictColumn column = new HeapDictColumn(4, false, interner);

    // when
    column.set(0, "processA");
    column.set(1, "processB");
    column.set(2, "processA");

    // then the same value at different rows gets the same code
    assertThat(column.type()).isEqualTo(ColumnType.STRING_DICT);
    assertThat(column.code(0)).isEqualTo(column.code(2));
    assertThat(column.code(0)).isNotEqualTo(column.code(1));
    assertThat(column.value(column.code(0))).isEqualTo("processA");
    assertThat(column.value(column.code(1))).isEqualTo("processB");
  }

  @Test
  void shouldShareCodesAcrossColumnsBackedByTheSameInterner() {
    // given two dict columns (as if from different segments) sharing one pipeline-wide interner
    final Interner interner = new Interner();
    final HeapDictColumn columnA = new HeapDictColumn(4, false, interner);
    final HeapDictColumn columnB = new HeapDictColumn(4, false, interner);

    // when
    columnA.set(0, "shared-value");
    columnB.set(0, "shared-value");

    // then
    assertThat(columnB.code(0)).isEqualTo(columnA.code(0));
  }

  @Test
  void shouldTrackNulls() {
    // given
    final Interner interner = new Interner();
    final HeapDictColumn column = new HeapDictColumn(4, true, interner);

    // when
    column.set(0, "value");
    column.setNull(1);

    // then
    assertThat(column.isNull(0)).isFalse();
    assertThat(column.isNull(1)).isTrue();
  }

  @Test
  void shouldRejectSetNullOnNonNullableColumn() {
    // given
    final Interner interner = new Interner();
    final HeapDictColumn column = new HeapDictColumn(4, false, interner);

    // when / then
    assertThatThrownBy(() -> column.setNull(0)).isInstanceOf(UnsupportedOperationException.class);
  }
}
