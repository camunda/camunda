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
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins key-only iteration: same ordering and cursor semantics as the key-value variants, and values
 * are never deserialized. The column family uses a value type that fails when read, so any attempt
 * to materialise a value fails the test.
 */
public abstract class AbstractKeyOnlyIterationTest extends ConformanceDbTestBase {

  private ColumnFamily<DbCompositeKey<DbLong, DbLong>, UnreadableValue> cf;

  @BeforeEach
  void createColumnFamily() {
    cf =
        columnFamily(
            Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new UnreadableValue());
  }

  @Test
  void shouldVisitKeysInUnsignedByteOrderWithForEachKey() {
    // given
    insert(1, 1);
    insert(-1, 1);
    insert(0, 1);
    insert(Long.MIN_VALUE, 1);
    insert(Long.MAX_VALUE, 1);

    // when
    final List<Long> visited = new ArrayList<>();
    final Consumer<DbCompositeKey<DbLong, DbLong>> consumer =
        key -> visited.add(key.first().getValue());
    cf.forEachKey(consumer);

    // then
    assertThat(visited).containsExactly(0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -1L);
  }

  @Test
  void shouldVisitKeysInOrderWithKeyVisitor() {
    // given
    insert(3, 1);
    insert(1, 2);
    insert(1, 1);

    // when
    final var visited = whileTrue(Integer.MAX_VALUE);

    // then
    assertThat(visited).containsExactly("1/1", "1/2", "3/1");
  }

  @Test
  void shouldStopWhenKeyVisitorReturnsFalse() {
    // given
    insert(1, 1);
    insert(2, 1);
    insert(3, 1);

    // when
    final var visited = whileTrue(2);

    // then
    assertThat(visited).containsExactly("1/1", "2/1");
  }

  @Test
  void shouldStartAtExistingStartKey() {
    // given
    insert(1, 1);
    insert(2, 1);
    insert(3, 1);

    // when
    final List<String> visited = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          visited.add(describe(key));
          return true;
        };
    cf.whileTrue(longPair(2, 1), visitor);

    // then
    assertThat(visited).containsExactly("2/1", "3/1");
  }

  @Test
  void shouldStartAtNextKeyIfStartKeyDoesNotExist() {
    // given
    insert(1, 1);
    insert(2, 1);
    insert(4, 1);

    // when
    final List<String> visited = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          visited.add(describe(key));
          return true;
        };
    cf.whileTrue(longPair(3, 0), visitor);

    // then
    assertThat(visited).containsExactly("4/1");
  }

  @Test
  void shouldVisitOnlyKeysOfPrefix() {
    // given
    insert(1, 1);
    insert(2, 2);
    insert(2, 1);
    insert(3, 1);

    // when
    final List<String> visited = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          visited.add(describe(key));
          return true;
        };
    cf.whileEqualPrefix(dbLong(2), visitor);

    // then
    assertThat(visited).containsExactly("2/1", "2/2");
  }

  @Test
  void shouldVisitOnlyKeysOfPrefixWithConsumer() {
    // given
    insert(1, 1);
    insert(2, 2);
    insert(2, 1);
    insert(3, 1);

    // when
    final List<String> visited = new ArrayList<>();
    final Consumer<DbCompositeKey<DbLong, DbLong>> consumer = key -> visited.add(describe(key));
    cf.whileEqualPrefix(dbLong(2), consumer);

    // then
    assertThat(visited).containsExactly("2/1", "2/2");
  }

  @Test
  void shouldStartPrefixScanAtNextKeyIfStartKeyDoesNotExist() {
    // given
    insert(2, 10);
    insert(2, 20);
    insert(2, 30);
    insert(3, 10);

    // when
    final List<String> visited = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          visited.add(describe(key));
          return true;
        };
    cf.whileEqualPrefix(dbLong(2), longPair(2, 15), visitor);

    // then
    assertThat(visited).containsExactly("2/20", "2/30");
  }

  @Test
  void shouldStopPrefixScanWhenVisitorReturnsFalse() {
    // given
    insert(2, 10);
    insert(2, 20);
    insert(2, 30);

    // when
    final List<String> visited = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          visited.add(describe(key));
          return false;
        };
    cf.whileEqualPrefix(dbLong(2), visitor);

    // then
    assertThat(visited).containsExactly("2/10");
  }

  @Test
  void shouldVisitKeysInReverseOrder() {
    // given
    insert(1, 1);
    insert(2, 1);
    insert(3, 1);

    // when
    final List<String> visited = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          visited.add(describe(key));
          return true;
        };
    cf.whileTrueReverse(longPair(2, 5), visitor);

    // then
    assertThat(visited).containsExactly("2/1", "1/1");
  }

  @Test
  void shouldVisitNothingOnEmptyColumnFamily() {
    // when
    final var visited = whileTrue(Integer.MAX_VALUE);
    final List<String> reverse = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          reverse.add(describe(key));
          return true;
        };
    cf.whileTrueReverse(longPair(5, 5), visitor);

    // then
    assertThat(visited).isEmpty();
    assertThat(reverse).isEmpty();
  }

  private void insert(final long first, final long second) {
    cf.insert(longPair(first, second), new UnreadableValue());
  }

  private List<String> whileTrue(final int limit) {
    final List<String> visited = new ArrayList<>();
    final KeyVisitor<DbCompositeKey<DbLong, DbLong>> visitor =
        key -> {
          visited.add(describe(key));
          return visited.size() < limit;
        };
    cf.whileTrue(visitor);
    return visited;
  }

  private static String describe(final DbCompositeKey<DbLong, DbLong> key) {
    return key.first().getValue() + "/" + key.second().getValue();
  }

  /** A value that can be written but throws when a backend tries to read it back. */
  private static final class UnreadableValue implements DbValue {

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      throw new AssertionError("value must not be materialised in key-only iteration");
    }

    @Override
    public int getLength() {
      return Long.BYTES;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putLong(offset, 42L);
      return Long.BYTES;
    }
  }
}
