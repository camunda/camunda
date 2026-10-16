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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins how numeric key components are ordered. Keys are serialized big-endian two's complement and
 * compared as unsigned bytes, so negative values sort <em>after</em> positive ones. Backends must
 * reproduce this, otherwise scans over keys holding negative components (e.g. inverted priorities)
 * return a different order.
 */
public abstract class AbstractNumericKeyOrderingTest extends ConformanceDbTestBase {

  @Test
  void shouldOrderNegativeLongKeysAfterPositiveOnes() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    for (final long key : new long[] {-1, Long.MIN_VALUE, 1, -2, Long.MAX_VALUE, 0}) {
      cf.insert(dbLong(key), dbLong(key));
    }

    // when
    final List<Long> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(key.getValue()));

    // then
    assertThat(visited).containsExactly(0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -2L, -1L);
  }

  @Test
  void shouldOrderNegativeLongKeysIndependentOfInsertionOrder() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    for (final long key : new long[] {0, 1, Long.MAX_VALUE, Long.MIN_VALUE, -2, -1}) {
      cf.insert(dbLong(key), dbLong(key));
    }

    // when
    final List<Long> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(key.getValue()));

    // then
    assertThat(visited).containsExactly(0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -2L, -1L);
  }

  @Test
  void shouldOrderNegativeIntKeysAfterPositiveOnes() {
    // given
    final ColumnFamily<DbInt, DbLong> cf = columnFamily(Cf.ONE, new DbInt(), new DbLong());
    for (final int key : new int[] {-1, Integer.MIN_VALUE, 1, -2, Integer.MAX_VALUE, 0}) {
      cf.insert(dbInt(key), dbLong(key));
    }

    // when
    final List<Integer> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(key.getValue()));

    // then
    assertThat(visited).containsExactly(0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE, -2, -1);
  }

  @Test
  void shouldSeekToNegativeStartKeyInUnsignedOrder() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    for (final long key : new long[] {0, 1, Long.MAX_VALUE, Long.MIN_VALUE, -2, -1}) {
      cf.insert(dbLong(key), dbLong(key));
    }

    // when
    final List<Long> visited = new ArrayList<>();
    cf.whileTrue(
        dbLong(Long.MIN_VALUE),
        (key, value) -> {
          visited.add(key.getValue());
          return true;
        });

    // then
    assertThat(visited).containsExactly(Long.MIN_VALUE, -2L, -1L);
  }

  @Test
  void shouldOrderCompositeKeyWithNegativeComponentInTheMiddle() {
    // given
    final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbLong>>, DbLong> cf =
        columnFamily(
            Cf.ONE,
            new DbCompositeKey<>(new DbLong(), new DbCompositeKey<>(new DbLong(), new DbLong())),
            new DbLong());
    // (type, middle, last)
    final long[][] entries = {
      {1, -1, 1}, {1, 5, 2}, {1, Long.MIN_VALUE, 3}, {1, 0, 4}, {0, -1, 5}, {2, 3, 6}, {1, 5, 1}
    };
    for (final long[] entry : entries) {
      cf.insert(
          new DbCompositeKey<>(dbLong(entry[0]), longPair(entry[1], entry[2])), dbLong(entry[2]));
    }

    // when
    final List<List<Long>> visited = new ArrayList<>();
    cf.forEach(
        (key, value) ->
            visited.add(
                List.of(
                    key.first().getValue(),
                    key.second().first().getValue(),
                    key.second().second().getValue())));

    // then
    assertThat(visited)
        .containsExactly(
            List.of(0L, -1L, 5L),
            List.of(1L, 0L, 4L),
            List.of(1L, 5L, 1L),
            List.of(1L, 5L, 2L),
            List.of(1L, Long.MIN_VALUE, 3L),
            List.of(1L, -1L, 1L),
            List.of(2L, 3L, 6L));
  }

  @Test
  void shouldOrderJobActivatableByPriorityShapedKeys() {
    // given - mirrors (type, invertedPriority, jobKey) of JOB_ACTIVATABLE_BY_PRIORITY
    final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbInt>, DbLong>, DbLong> cf =
        columnFamily(
            Cf.ONE,
            new DbCompositeKey<>(new DbCompositeKey<>(new DbString(), new DbInt()), new DbLong()),
            new DbLong());
    final int[] priorities = {Integer.MIN_VALUE, 0, 100, -1, 1};
    for (final int priority : priorities) {
      cf.insert(jobKey("type", Integer.MAX_VALUE - priority, priority), dbLong(priority));
    }
    cf.insert(jobKey("type", Integer.MAX_VALUE - 1, 99), dbLong(99));

    // when
    final List<Long> jobKeysInScanOrder = new ArrayList<>();
    cf.forEach((key, value) -> jobKeysInScanOrder.add(key.second().getValue()));

    // then - higher priority first; the inversion overflows for negative priorities, which lands
    // them after every non-negative one, and the lowest priority comes last
    assertThat(jobKeysInScanOrder)
        .containsExactly(100L, 1L, 99L, 0L, -1L, (long) Integer.MIN_VALUE);
  }

  @Test
  void shouldOnlyScanJobActivatableShapedKeysOfRequestedType() {
    // given
    final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbInt>, DbLong>, DbLong> cf =
        columnFamily(
            Cf.ONE,
            new DbCompositeKey<>(new DbCompositeKey<>(new DbString(), new DbInt()), new DbLong()),
            new DbLong());
    for (final int priority : new int[] {100, 1, 0, -1, Integer.MIN_VALUE}) {
      cf.insert(jobKey("wanted", Integer.MAX_VALUE - priority, priority), dbLong(priority));
      cf.insert(jobKey("other", Integer.MAX_VALUE - priority, priority), dbLong(priority));
    }

    // when
    final List<Long> jobKeys = new ArrayList<>();
    cf.whileEqualPrefix(
        dbString("wanted"),
        (key, value) -> {
          jobKeys.add(key.second().getValue());
          return true;
        });

    // then
    assertThat(jobKeys).containsExactly(100L, 1L, 0L, -1L, (long) Integer.MIN_VALUE);
  }

  private static DbCompositeKey<DbCompositeKey<DbString, DbInt>, DbLong> jobKey(
      final String type, final int invertedPriority, final long jobKey) {
    return new DbCompositeKey<>(
        new DbCompositeKey<>(dbString(type), dbInt(invertedPriority)), dbLong(jobKey));
  }
}
