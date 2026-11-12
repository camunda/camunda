/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GroupCacheTest {

  private static final EntityType<String, PartitionStateRow> ROW =
      new EntityType<>(
          PartitionStateMapper.class,
          "PartitionState",
          "test row",
          PartitionStateRow::key,
          key -> key.substring(0, 1));

  @Test
  void shouldNotChangeAGroupThatWasAlreadyReturned() {
    // given a cached group that a reader got
    final var cache = new GroupCache(10);
    cache.put(ROW, "a", Map.of("a1", "row1"));
    final var returned = cache.get(ROW, "a");

    // when a commit changes the group
    cache.apply(ROW, "a2", "row2");

    // then the reader's rows are unchanged, and a new read sees the change
    assertThat(returned).containsOnlyKeys("a1");
    assertThat(cache.get(ROW, "a")).containsOnlyKeys("a1", "a2");
  }

  @Test
  void shouldReturnRowsThatCannotBeChanged() {
    // given
    final var cache = new GroupCache(10);
    cache.put(ROW, "a", Map.of("a1", "row1"));

    // when
    final var returned = cache.get(ROW, "a");

    // then
    assertThatThrownBy(() -> returned.put("a2", "row2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
