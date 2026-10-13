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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * Pins what happens when the visitor mutates the database while an iteration is running: deleting
 * the entry just handed to the visitor is safe and does not skip or repeat entries, other column
 * families can be modified, and iterations on different column families can be nested.
 */
public abstract class AbstractIterationMutationTest extends ConformanceDbTestBase {

  private static final long COUNT = 25;

  @Test
  void shouldVisitEveryKeyOnceWhenDeletingVisitedKeyInForEach() {
    // given
    final var cf = filledColumnFamily(Cf.ONE);

    // when
    final List<Long> visited = new ArrayList<>();
    cf.forEach(
        (key, value) -> {
          visited.add(key.getValue());
          cf.deleteExisting(key);
        });

    // then
    assertThat(visited).containsExactlyElementsOf(expectedKeys());
    assertThat(cf.isEmpty()).isTrue();
    assertThat(cf.count()).isZero();
  }

  @Test
  void shouldVisitEveryKeyOnceWhenDeletingVisitedKeyInWhileTrue() {
    // given
    final var cf = filledColumnFamily(Cf.ONE);

    // when
    final List<Long> visited = new ArrayList<>();
    cf.whileTrue(
        (key, value) -> {
          visited.add(key.getValue());
          cf.deleteExisting(key);
          return true;
        });

    // then
    assertThat(visited).containsExactlyElementsOf(expectedKeys());
    assertThat(cf.isEmpty()).isTrue();
  }

  @Test
  void shouldVisitEveryKeyOnceWhenDeletingVisitedKeyInKeyOnlyIteration() {
    // given
    final var cf = filledColumnFamily(Cf.ONE);

    // when
    final List<Long> visited = new ArrayList<>();
    final KeyVisitor<DbLong> visitor =
        key -> {
          visited.add(key.getValue());
          cf.deleteExisting(key);
          return true;
        };
    cf.whileTrue(visitor);

    // then
    assertThat(visited).containsExactlyElementsOf(expectedKeys());
    assertThat(cf.isEmpty()).isTrue();
  }

  @Test
  void shouldVisitEveryKeyOnceWhenDeletingVisitedKeyInPrefixScan() {
    // given
    final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> cf =
        columnFamily(Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
    for (long i = 0; i < COUNT; i++) {
      cf.insert(longPair(1, i), dbLong(i));
      cf.insert(longPair(2, i), dbLong(i));
    }

    // when
    final List<Long> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(1),
        (key, value) -> {
          visited.add(key.second().getValue());
          cf.deleteExisting(key);
          return true;
        });

    // then
    assertThat(visited).containsExactlyElementsOf(expectedKeys());
    assertThat(cf.count()).isEqualTo(COUNT);
    assertThat(cf.countEqualPrefix(dbLong(1))).isZero();
    assertThat(cf.countEqualPrefix(dbLong(2))).isEqualTo(COUNT);
  }

  @Test
  void shouldVisitEveryKeyOnceWhenDeletingVisitedKeyInReverseIteration() {
    // given
    final var cf = filledColumnFamily(Cf.ONE);

    // when
    final List<Long> visited = new ArrayList<>();
    cf.whileTrueReverse(
        dbLong(COUNT * 2),
        (key, value) -> {
          visited.add(key.getValue());
          cf.deleteExisting(key);
          return true;
        });

    // then
    final List<Long> expected = new ArrayList<>(expectedKeys());
    java.util.Collections.reverse(expected);
    assertThat(visited).containsExactlyElementsOf(expected);
    assertThat(cf.isEmpty()).isTrue();
  }

  @Test
  void shouldDeleteFromDifferentColumnFamilyWhileIterating() {
    // given
    final var iterated = filledColumnFamily(Cf.ONE);
    final var other = filledColumnFamily(Cf.TWO);

    // when
    final List<Long> visited = new ArrayList<>();
    iterated.forEach(
        (key, value) -> {
          visited.add(key.getValue());
          other.deleteExisting(dbLong(key.getValue()));
        });

    // then
    assertThat(visited).containsExactlyElementsOf(expectedKeys());
    assertThat(iterated.count()).isEqualTo(COUNT);
    assertThat(other.isEmpty()).isTrue();
  }

  @Test
  void shouldInsertIntoDifferentColumnFamilyWhileIterating() {
    // given
    final var iterated = filledColumnFamily(Cf.ONE);
    final var other = longColumnFamily(Cf.TWO);

    // when
    final List<Long> visited = new ArrayList<>();
    iterated.forEach(
        (key, value) -> {
          visited.add(key.getValue());
          other.insert(dbLong(key.getValue()), dbLong(1));
        });

    // then
    assertThat(visited).containsExactlyElementsOf(expectedKeys());
    assertThat(other.count()).isEqualTo(COUNT);
  }

  @Test
  void shouldNestIterationsOnDifferentColumnFamiliesAndDeleteFromInnerOne() {
    // given
    final ColumnFamily<DbLong, DbLong> outer = longColumnFamily(Cf.ONE);
    final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> inner =
        columnFamily(Cf.TWO, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
    for (long o = 1; o <= 4; o++) {
      outer.insert(dbLong(o), dbLong(o));
      for (long i = 1; i <= 3; i++) {
        inner.insert(longPair(o, i), dbLong(i));
      }
    }

    // when
    final List<String> visited = new ArrayList<>();
    outer.forEach(
        (outerKey, outerValue) -> {
          final long outerId = outerKey.getValue();
          inner.whileEqualPrefix(
              dbLong(outerId),
              (innerKey, innerValue) -> {
                visited.add(outerId + "/" + innerKey.second().getValue());
                inner.deleteExisting(innerKey);
                return true;
              });
        });

    // then
    assertThat(visited)
        .containsExactly(
            "1/1", "1/2", "1/3", "2/1", "2/2", "2/3", "3/1", "3/2", "3/3", "4/1", "4/2", "4/3");
    assertThat(outer.count()).isEqualTo(4);
    assertThat(inner.isEmpty()).isTrue();
  }

  @Test
  void shouldNestFullScansAndDeleteVisitedKeysOfBothColumnFamilies() {
    // given
    final var outer = filledColumnFamily(Cf.ONE);
    final var inner = filledColumnFamily(Cf.TWO);

    // when
    final List<Long> outerVisited = new ArrayList<>();
    final List<Long> innerVisited = new ArrayList<>();
    outer.forEach(
        (outerKey, outerValue) -> {
          outerVisited.add(outerKey.getValue());
          outer.deleteExisting(outerKey);
          inner.forEach(
              (innerKey, innerValue) -> {
                innerVisited.add(innerKey.getValue());
                inner.deleteExisting(innerKey);
              });
        });

    // then - the first outer step drains the inner column family, later steps see it empty
    assertThat(outerVisited).containsExactlyElementsOf(expectedKeys());
    assertThat(innerVisited).containsExactlyElementsOf(expectedKeys());
    assertThat(outer.isEmpty()).isTrue();
    assertThat(inner.isEmpty()).isTrue();
  }

  private ColumnFamily<DbLong, DbLong> filledColumnFamily(final Cf cf) {
    final var columnFamily = longColumnFamily(cf);
    for (long i = 0; i < COUNT; i++) {
      columnFamily.insert(dbLong(i), dbLong(i));
    }
    return columnFamily;
  }

  private static List<Long> expectedKeys() {
    return LongStream.range(0, COUNT).boxed().toList();
  }
}
