/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.api.ListOptions.Order;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class ListOptionsTest {

  @Test
  void shouldSelectNewestFirstWithinLimit() {
    // given
    final var ids = List.of(id(1, 3), id(1, 10), id(1, 2), id(1, 100));
    final var options = ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(3));

    // when
    final var selected = options.select(ids, Function.identity());

    // then
    assertThat(selected).containsExactly(id(1, 100), id(1, 10), id(1, 3));
  }

  @Test
  void shouldSelectOldestFirstAfterCursor() {
    // given
    final var ids = List.of(id(1, 3), id(1, 10), id(1, 2), id(1, 100));
    final var options = ListOptions.oldestFirst(OptionalLong.of(2), OptionalInt.of(2));

    // when
    final var selected = options.select(ids, Function.identity());

    // then
    assertThat(selected).containsExactly(id(1, 3), id(1, 10));
  }

  @Test
  void shouldSelectNewestFirstBeforeCursor() {
    // given
    final var ids = List.of(id(1, 3), id(1, 10), id(1, 2), id(1, 100));
    final var options = ListOptions.newestFirst(OptionalLong.of(10), OptionalInt.empty());

    // when
    final var selected = options.select(ids, Function.identity());

    // then
    assertThat(selected).containsExactly(id(1, 3), id(1, 2));
  }

  @Test
  void shouldCountLimitByCheckpointIdAndKeepAllCopies() {
    // given
    final var ids = List.of(id(1, 1), id(2, 1), id(1, 2), id(2, 2), id(1, 3));
    final var options = ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(2));

    // when
    final var selected = options.select(ids, Function.identity());

    // then
    assertThat(selected).containsExactly(id(1, 3), id(1, 2), id(2, 2));
  }

  @Test
  void shouldOrderCheckpointIdsNumerically() {
    // given
    final var checkpointIds = List.of(1L, 2L, 10L, 100L, 1_700_000_000_000L);

    // when
    final var newestFirst = ListOptions.all().selectCheckpointIds(checkpointIds);
    final var oldestFirst =
        ListOptions.oldestFirst(OptionalLong.empty(), OptionalInt.empty())
            .selectCheckpointIds(checkpointIds);

    // then
    assertThat(newestFirst).containsExactly(1_700_000_000_000L, 100L, 10L, 2L, 1L);
    assertThat(oldestFirst).containsExactly(1L, 2L, 10L, 100L, 1_700_000_000_000L);
  }

  @Test
  void shouldSelectAllWhenUnbounded() {
    // given
    final var checkpointIds = List.of(5L, 5L, 7L, 6L);

    // when
    final var selected = ListOptions.all().selectCheckpointIds(checkpointIds);

    // then
    assertThat(selected).containsExactly(7L, 6L, 5L);
    assertThat(ListOptions.all().order()).isEqualTo(Order.DESCENDING);
  }

  @Test
  void shouldRejectNonPositiveLimit() {
    assertThatThrownBy(() -> ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit");
  }

  private static BackupIdentifier id(final int nodeId, final long checkpointId) {
    return new BackupIdentifierImpl(nodeId, 1, checkpointId);
  }
}
