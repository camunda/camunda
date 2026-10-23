/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A SQLite cursor can return a row again that its visitor has updated, RocksDB iterators never
 * revisit a key. The scan must visit every key once whatever the visitor updates. The preconditions
 * are enabled so that the scan also checks its own ordering.
 */
final class SqliteUpdateDuringIterationTest extends ConformanceDbTestBase {

  /** More than the largest batch a scan reads at once. */
  private static final long COUNT = 700;

  private static final String LONG_VALUE = "a much longer value ".repeat(20);

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>(new ConsistencyChecksSettings(true, false));
  }

  @Test
  void shouldVisitEachKeyOnceWhenUpdatingVisitedKeyOfPrefixScan() {
    // given
    final var cf = filledPairs();

    // when
    final List<Long> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(1),
        (key, value) -> {
          visited.add(key.second().getValue());
          cf.update(longPair(1, key.second().getValue()), dbString(LONG_VALUE));
        });

    // then
    assertThat(visited).hasSize((int) COUNT).doesNotHaveDuplicates().isSorted();
  }

  @Test
  void shouldVisitEachKeyOnceWhenUpdatingVisitedKeyInForEach() {
    // given
    final var cf = filledStrings();

    // when
    final List<Long> visited = new ArrayList<>();
    cf.forEach(
        (key, value) -> {
          visited.add(key.getValue());
          cf.update(key, dbString(LONG_VALUE));
        });

    // then
    assertThat(visited).hasSize((int) COUNT).doesNotHaveDuplicates().isSorted();
  }

  @Test
  void shouldVisitEachKeyOnceWhenUpdatingVisitedKeyInKeyOnlyIteration() {
    // given
    final var cf = filledStrings();

    // when
    final List<Long> visited = new ArrayList<>();
    cf.forEachKey(
        key -> {
          visited.add(key.getValue());
          cf.update(key, dbString(LONG_VALUE));
        });

    // then
    assertThat(visited).hasSize((int) COUNT).doesNotHaveDuplicates().isSorted();
  }

  @Test
  void shouldVisitEachKeyOnceWhenUpdatingVisitedKeyInReverseIteration() {
    // given
    final var cf = filledStrings();

    // when
    final List<Long> visited = new ArrayList<>();
    cf.whileTrueReverse(
        dbLong(COUNT),
        (key, value) -> {
          visited.add(key.getValue());
          cf.update(key, dbString(LONG_VALUE));
          return true;
        });

    // then
    assertThat(visited)
        .hasSize((int) COUNT)
        .doesNotHaveDuplicates()
        .isSortedAccordingTo((a, b) -> Long.compare(b, a));
  }

  @Test
  void shouldVisitEachKeyOnceWhenUpdatingAllRowsOnFirstVisit() {
    // given
    final var cf = filledStrings();

    // when
    final List<Long> visited = new ArrayList<>();
    cf.forEach(
        (key, value) -> {
          if (visited.isEmpty()) {
            for (long i = 0; i < COUNT; i++) {
              cf.update(dbLong(i), dbString(LONG_VALUE));
            }
          }
          visited.add(key.getValue());
        });

    // then
    assertThat(visited).hasSize((int) COUNT).doesNotHaveDuplicates();
  }

  @Test
  void shouldVisitUpdatedValueOfKeyAheadOfCursor() {
    // given
    final var cf = filledStrings();

    // when the first visit updates a key that is ahead of the cursor
    final long ahead = COUNT - 1;
    final var seen = new ArrayList<String>();
    cf.forEach(
        (key, value) -> {
          if (key.getValue() == 0) {
            cf.update(dbLong(ahead), dbString("updated"));
          }
          if (key.getValue() == ahead) {
            seen.add(value.toString());
          }
        });

    // then it sees the new value, as an iterator over a RocksDB transaction does
    assertThat(seen).containsExactly("updated");
  }

  @Test
  void shouldVisitEachKeyOnceWhenUpdatingWithinOpenTransaction() {
    // given
    final var cf = filledStrings();

    // when
    final List<Long> visited = new ArrayList<>();
    context.runInTransaction(
        () ->
            cf.forEach(
                (key, value) -> {
                  visited.add(key.getValue());
                  cf.upsert(key, dbString(LONG_VALUE));
                }));

    // then
    assertThat(visited).hasSize((int) COUNT).doesNotHaveDuplicates();
  }

  private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbString> filledPairs() {
    final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbString> cf =
        columnFamily(Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbString());
    for (long i = 0; i < COUNT; i++) {
      cf.insert(longPair(1, i), dbString("a"));
    }
    return cf;
  }

  private ColumnFamily<DbLong, DbString> filledStrings() {
    final ColumnFamily<DbLong, DbString> cf = columnFamily(Cf.ONE, new DbLong(), new DbString());
    for (long i = 0; i < COUNT; i++) {
      cf.insert(dbLong(i), dbString("a"));
    }
    return cf;
  }
}
