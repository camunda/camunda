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

class HeapDoubleColumnTest {

  @Test
  void shouldRoundTripValues() {
    // given
    final HeapDoubleColumn column = new HeapDoubleColumn(8, false);

    // when
    column.set(0, 42.5);
    column.set(1, -1.25);
    column.set(7, Double.MAX_VALUE);

    // then
    assertThat(column.type()).isEqualTo(ColumnType.DOUBLE);
    assertThat(column.get(0)).isEqualTo(42.5);
    assertThat(column.get(1)).isEqualTo(-1.25);
    assertThat(column.get(7)).isEqualTo(Double.MAX_VALUE);
  }

  @Test
  void shouldTrackNullsIndependentlyOfValues() {
    // given
    final HeapDoubleColumn column = new HeapDoubleColumn(4, true);

    // when
    column.set(0, 7.0);
    column.setNull(1);

    // then
    assertThat(column.isNull(0)).isFalse();
    assertThat(column.isNull(1)).isTrue();
  }

  @Test
  void shouldClearNullOnSubsequentSet() {
    // given
    final HeapDoubleColumn column = new HeapDoubleColumn(4, true);
    column.setNull(0);

    // when
    column.set(0, 5.5);

    // then
    assertThat(column.isNull(0)).isFalse();
    assertThat(column.get(0)).isEqualTo(5.5);
  }

  @Test
  void shouldRejectSetNullOnNonNullableColumn() {
    // given
    final HeapDoubleColumn column = new HeapDoubleColumn(4, false);

    // when / then
    assertThatThrownBy(() -> column.setNull(0)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRewindNullMaskOnReset() {
    // given
    final HeapDoubleColumn column = new HeapDoubleColumn(4, true);
    column.setNull(2);

    // when
    column.reset();

    // then
    assertThat(column.isNull(2)).isFalse();
  }
}
