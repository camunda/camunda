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
import io.camunda.zeebe.db.impl.DbLong;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pins prefix scan and start-key semantics of {@code whileEqualPrefix}. */
public abstract class AbstractPrefixScanTest extends ConformanceDbTestBase {

  private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> cf;

  @BeforeEach
  void createColumnFamily() {
    cf = columnFamily(Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
  }

  @Test
  void shouldVisitOnlyKeysWithMatchingPrefixInKeyOrder() {
    // given
    insert(2, 20);
    insert(1, 11);
    insert(2, 10);
    insert(3, 30);
    insert(1, 12);

    // when
    final var visited = scan(2);

    // then
    assertThat(visited).containsExactly("2/10", "2/20");
  }

  @Test
  void shouldNotMatchKeysWhoseLeadingBytesDifferFromThePrefix() {
    // given - 1, 256 and 1 << 56 share bytes with 1 in different positions
    insert(1, 1);
    insert(256, 1);
    insert(1L << 56, 1);
    insert(2, 1);

    // when
    final var visited = scan(1);

    // then
    assertThat(visited).containsExactly("1/1");
  }

  @Test
  void shouldMatchPrefixOfAllZeroBytes() {
    // given
    insert(0, 5);
    insert(0, 6);
    insert(1, 0);

    // when
    final var visited = scan(0);

    // then
    assertThat(visited).containsExactly("0/5", "0/6");
  }

  @Test
  void shouldMatchPrefixOfAllOneBytesAtTheEndOfTheColumnFamily() {
    // given
    insert(-1, 5);
    insert(-1, 6);
    insert(1, 0);

    // when
    final var visited = scan(-1);

    // then
    assertThat(visited).containsExactly("-1/5", "-1/6");
  }

  @Test
  void shouldNotLeakIntoNeighbouringColumnFamilies() {
    // given
    insert(1, 1);
    final var other =
        columnFamily(Cf.TWO, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
    other.insert(longPair(1, 2), dbLong(0));
    final var before =
        columnFamily(Cf.DEFAULT, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
    before.insert(longPair(1, 3), dbLong(0));

    // when
    final var visited = scan(1);

    // then
    assertThat(visited).containsExactly("1/1");
  }

  @Test
  void shouldVisitNothingIfNoKeyHasThePrefix() {
    // given
    insert(1, 1);
    insert(3, 1);

    // when
    final var visited = scan(2);

    // then
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldVisitNothingOnEmptyColumnFamily() {
    // when
    final var visited = scan(1);

    // then
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldStartAtExistingStartKey() {
    // given
    insert(1, 10);
    insert(2, 10);
    insert(2, 20);
    insert(2, 30);
    insert(3, 10);

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(2),
        longPair(2, 20),
        (key, value) -> {
          visited.add(describe(key));
          return true;
        });

    // then
    assertThat(visited).containsExactly("2/20", "2/30");
  }

  @Test
  void shouldStartAtNextKeyIfStartKeyDoesNotExist() {
    // given
    insert(1, 10);
    insert(2, 10);
    insert(2, 20);
    insert(2, 30);
    insert(3, 10);

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(2),
        longPair(2, 15),
        (key, value) -> {
          visited.add(describe(key));
          return true;
        });

    // then
    assertThat(visited).containsExactly("2/20", "2/30");
  }

  @Test
  void shouldVisitNothingIfAbsentStartKeyIsBehindAllKeysOfThePrefix() {
    // given - the next key after the start key belongs to another prefix
    insert(2, 10);
    insert(2, 20);
    insert(3, 10);

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(2),
        longPair(2, 25),
        (key, value) -> {
          visited.add(describe(key));
          return true;
        });

    // then
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldVisitAllKeysOfPrefixIfAbsentStartKeyIsBeforeThem() {
    // given
    insert(2, 10);
    insert(2, 20);
    insert(3, 10);

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(2),
        longPair(2, 5),
        (key, value) -> {
          visited.add(describe(key));
          return true;
        });

    // then
    assertThat(visited).containsExactly("2/10", "2/20");
  }

  @Test
  void shouldStopWhenVisitorReturnsFalse() {
    // given
    insert(2, 10);
    insert(2, 20);
    insert(2, 30);

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(2),
        (key, value) -> {
          visited.add(describe(key));
          return visited.size() < 2;
        });

    // then
    assertThat(visited).containsExactly("2/10", "2/20");
  }

  @Test
  void shouldStopWhenVisitorWithStartKeyReturnsFalse() {
    // given
    insert(2, 10);
    insert(2, 20);
    insert(2, 30);
    insert(2, 40);

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(2),
        longPair(2, 20),
        (key, value) -> {
          visited.add(describe(key));
          return false;
        });

    // then
    assertThat(visited).containsExactly("2/20");
  }

  @Test
  void shouldVisitValuesAlongsideKeys() {
    // given
    cf.insert(longPair(2, 10), dbLong(100));
    cf.insert(longPair(2, 20), dbLong(200));
    cf.insert(longPair(3, 30), dbLong(300));

    // when
    final List<Long> values = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(2),
        (key, value) -> {
          values.add(value.getValue());
        });

    // then
    assertThat(values).containsExactly(100L, 200L);
  }

  @Test
  void shouldScanNestedPrefixOfThreeComponentKey() {
    // given
    final ColumnFamily<DbCompositeKey<DbCompositeKey<DbLong, DbLong>, DbLong>, DbLong> three =
        columnFamily(
            Cf.THREE,
            new DbCompositeKey<>(new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong()),
            new DbLong());
    three.insert(new DbCompositeKey<>(longPair(1, 2), dbLong(1)), dbLong(0));
    three.insert(new DbCompositeKey<>(longPair(1, 2), dbLong(2)), dbLong(0));
    three.insert(new DbCompositeKey<>(longPair(1, 3), dbLong(1)), dbLong(0));
    three.insert(new DbCompositeKey<>(longPair(2, 2), dbLong(1)), dbLong(0));

    // when
    final List<String> shortPrefix = new ArrayList<>();
    three.whileEqualPrefix(
        dbLong(1),
        (key, value) -> {
          shortPrefix.add(
              key.first().first().getValue()
                  + "/"
                  + key.first().second().getValue()
                  + "/"
                  + key.second().getValue());
          return true;
        });
    final List<Long> longPrefix = new ArrayList<>();
    three.whileEqualPrefix(
        longPair(1, 2),
        (key, value) -> {
          longPrefix.add(key.second().getValue());
          return true;
        });

    // then
    assertThat(shortPrefix).containsExactly("1/2/1", "1/2/2", "1/3/1");
    assertThat(longPrefix).containsExactly(1L, 2L);
  }

  private void insert(final long first, final long second) {
    cf.insert(longPair(first, second), dbLong(0));
  }

  private List<String> scan(final long prefix) {
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbLong(prefix),
        (key, value) -> {
          visited.add(describe(key));
          return true;
        });
    return visited;
  }

  private static String describe(final DbCompositeKey<DbLong, DbLong> key) {
    return key.first().getValue() + "/" + key.second().getValue();
  }
}
