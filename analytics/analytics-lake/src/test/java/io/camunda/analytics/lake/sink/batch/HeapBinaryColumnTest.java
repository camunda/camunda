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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HeapBinaryColumnTest {

  @Test
  void shouldRoundTripValuesInAppendOrder() {
    // given
    final HeapBinaryColumn column = new HeapBinaryColumn(4, false, 8);
    final byte[] rowA = "hello".getBytes(StandardCharsets.UTF_8);
    final byte[] rowB = "hi".getBytes(StandardCharsets.UTF_8);

    // when
    column.set(0, rowA, 0, rowA.length);
    column.set(1, rowB, 0, rowB.length);

    // then
    assertThat(column.type()).isEqualTo(ColumnType.BINARY);
    assertThat(column.length(0)).isEqualTo(rowA.length);
    assertThat(column.length(1)).isEqualTo(rowB.length);
    final byte[] dst = new byte[rowA.length];
    final int copied = column.copyTo(0, dst, 0);
    assertThat(copied).isEqualTo(rowA.length);
    assertThat(dst).isEqualTo(rowA);
  }

  @Test
  void shouldCopyOnlyTheRequestedSlice() {
    // given
    final HeapBinaryColumn column = new HeapBinaryColumn(4, false, 8);
    final byte[] src = "abcdef".getBytes(StandardCharsets.UTF_8);

    // when: only bytes [2,5) go into row 0
    column.set(0, src, 2, 3);

    // then
    assertThat(column.length(0)).isEqualTo(3);
    final byte[] dst = new byte[3];
    column.copyTo(0, dst, 0);
    assertThat(dst).isEqualTo(new byte[] {'c', 'd', 'e'});
  }

  @Test
  void shouldTrackNullsAndKeepOffsetChainValidForFollowingRow() {
    // given
    final HeapBinaryColumn column = new HeapBinaryColumn(4, true, 8);
    final byte[] rowA = "x".getBytes(StandardCharsets.UTF_8);
    final byte[] rowC = "yz".getBytes(StandardCharsets.UTF_8);

    // when: row 1 is null, sandwiched between two real rows
    column.set(0, rowA, 0, rowA.length);
    column.setNull(1);
    column.set(2, rowC, 0, rowC.length);

    // then row 2 must still read back correctly despite the null gap
    assertThat(column.isNull(1)).isTrue();
    assertThat(column.length(2)).isEqualTo(rowC.length);
    final byte[] dst = new byte[rowC.length];
    column.copyTo(2, dst, 0);
    assertThat(dst).isEqualTo(rowC);
  }

  @Test
  void shouldRejectSetNullOnNonNullableColumn() {
    // given
    final HeapBinaryColumn column = new HeapBinaryColumn(4, false, 8);

    // when / then
    assertThatThrownBy(() -> column.setNull(0)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectRowExceedingRemainingArenaSpaceInsteadOfGrowing() {
    // given a 2-row arena budgeted at 4 bytes/row (8 bytes total), with row 0 already using all of
    // it
    final HeapBinaryColumn column = new HeapBinaryColumn(2, false, 4);
    final byte[] fillsArena = new byte[8];
    column.set(0, fillsArena, 0, 8);
    final byte[] oneMoreByte = new byte[] {9};

    // when / then: row 1 must be rejected, not silently grow the arena
    assertThatThrownBy(() -> column.set(1, oneMoreByte, 0, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("arena exhausted");
  }

  @Test
  void shouldReuseArenaAfterReset() {
    // given
    final HeapBinaryColumn column = new HeapBinaryColumn(2, false, 4);
    final byte[] value = new byte[] {1, 2, 3, 4};
    column.set(0, value, 0, value.length);
    column.set(1, value, 0, value.length);

    // when
    column.reset();
    final byte[] next = new byte[] {5, 6};
    column.set(0, next, 0, next.length);

    // then: the arena position rewound, so the first row after reset starts at offset 0 again
    assertThat(column.length(0)).isEqualTo(2);
    final byte[] dst = new byte[2];
    column.copyTo(0, dst, 0);
    assertThat(dst).isEqualTo(next);
  }
}
