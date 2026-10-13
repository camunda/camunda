/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.impl.DbLong;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code whileTrueReverse}: iteration starts at the greatest key that is less than or equal to
 * the start key (seek-for-prev semantics) and walks towards the smallest key.
 */
public abstract class AbstractReverseIterationTest extends ConformanceDbTestBase {

  private ColumnFamily<DbLong, DbLong> cf;

  @BeforeEach
  void createColumnFamily() {
    cf = longColumnFamily(Cf.ONE);
  }

  @Test
  void shouldStartAtStartKeyIfItExists() {
    // given
    insert(1, 2, 3, 4, 5, 6);

    // when
    final var visited = reverseFrom(5, Integer.MAX_VALUE);

    // then
    assertThat(visited).containsExactly(5L, 4L, 3L, 2L, 1L);
  }

  @Test
  void shouldStartAtPreviousKeyIfStartKeyDoesNotExist() {
    // given
    insert(1, 2, 3, 4, 6, 7);

    // when
    final var visited = reverseFrom(5, Integer.MAX_VALUE);

    // then
    assertThat(visited).containsExactly(4L, 3L, 2L, 1L);
  }

  @Test
  void shouldHonourEarlyExit() {
    // given
    insert(1, 2, 3, 4, 5, 6);

    // when
    final var visited = reverseFrom(5, 4);

    // then
    assertThat(visited).containsExactly(5L, 4L, 3L, 2L);
  }

  @Test
  void shouldVisitNothingIfStartKeyIsBelowAllKeys() {
    // given
    insert(10, 20, 30);

    // when
    final var visited = reverseFrom(5, Integer.MAX_VALUE);

    // then
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldVisitAllKeysIfStartKeyIsAboveAllKeys() {
    // given
    insert(10, 20, 30);

    // when
    final var visited = reverseFrom(99, Integer.MAX_VALUE);

    // then
    assertThat(visited).containsExactly(30L, 20L, 10L);
  }

  @Test
  void shouldVisitNothingOnEmptyColumnFamily() {
    // when
    final var visited = reverseFrom(5, Integer.MAX_VALUE);

    // then
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldNotLeakIntoNeighbouringColumnFamilies() {
    // given
    insert(10, 20);
    final var before = longColumnFamily(Cf.DEFAULT);
    before.insert(dbLong(1), dbLong(0));
    final var after = longColumnFamily(Cf.TWO);
    after.insert(dbLong(1000), dbLong(0));

    // when
    final var fromBelow = reverseFrom(5, Integer.MAX_VALUE);
    final var fromAbove = reverseFrom(5000, Integer.MAX_VALUE);

    // then
    assertThat(fromBelow).isEmpty();
    assertThat(fromAbove).containsExactly(20L, 10L);
  }

  @Test
  void shouldWalkInUnsignedByteOrderForNegativeKeys() {
    // given
    insert(0, 1, Long.MAX_VALUE, Long.MIN_VALUE, -2, -1);

    // when
    final var fromMin = reverseFrom(Long.MIN_VALUE, Integer.MAX_VALUE);
    final var fromAbsentNegative = reverseFrom(-3, Integer.MAX_VALUE);

    // then
    assertThat(fromMin).containsExactly(Long.MIN_VALUE, Long.MAX_VALUE, 1L, 0L);
    assertThat(fromAbsentNegative).containsExactly(Long.MIN_VALUE, Long.MAX_VALUE, 1L, 0L);
  }

  @Test
  void shouldSupportKeyOnlyVisitorFromExistingStartKey() {
    // given
    insert(1, 2, 3, 4, 5, 6);

    // when
    final var visited = reverseKeysFrom(5, Integer.MAX_VALUE);

    // then
    assertThat(visited).containsExactly(5L, 4L, 3L, 2L, 1L);
  }

  @Test
  void shouldSupportKeyOnlyVisitorFromAbsentStartKey() {
    // given
    insert(1, 2, 3, 4, 6, 7);

    // when
    final var visited = reverseKeysFrom(5, Integer.MAX_VALUE);

    // then
    assertThat(visited).containsExactly(4L, 3L, 2L, 1L);
  }

  @Test
  void shouldHonourEarlyExitOfKeyOnlyVisitor() {
    // given
    insert(1, 2, 3, 4, 5, 6);

    // when
    final var visited = reverseKeysFrom(6, 2);

    // then
    assertThat(visited).containsExactly(6L, 5L);
  }

  private void insert(final long... keys) {
    for (final long key : keys) {
      cf.insert(dbLong(key), dbLong(key));
    }
  }

  private List<Long> reverseFrom(final long startAt, final int limit) {
    final List<Long> visited = new ArrayList<>();
    cf.whileTrueReverse(
        dbLong(startAt),
        (key, value) -> {
          visited.add(key.getValue());
          return visited.size() < limit;
        });
    return visited;
  }

  private List<Long> reverseKeysFrom(final long startAt, final int limit) {
    final List<Long> visited = new ArrayList<>();
    final KeyVisitor<DbLong> visitor =
        key -> {
          visited.add(key.getValue());
          return visited.size() < limit;
        };
    cf.whileTrueReverse(dbLong(startAt), visitor);
    return visited;
  }
}
