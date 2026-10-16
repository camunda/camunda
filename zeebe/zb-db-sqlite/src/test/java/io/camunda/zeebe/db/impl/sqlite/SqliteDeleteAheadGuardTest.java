/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A SQLite cursor silently skips rows that are deleted ahead of it, RocksDB iterators do not. The
 * guard turns this into a failure, which is enabled with the preconditions only.
 */
final class SqliteDeleteAheadGuardTest extends ConformanceDbTestBase {

  private static final long COUNT = 10;

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>(new ConsistencyChecksSettings(true, false));
  }

  @Test
  void shouldRejectDeletingKeyAheadOfForwardCursor() {
    // given
    final var cf = filled(Cf.ONE);

    // then
    assertThatThrownBy(() -> cf.forEach((key, value) -> cf.deleteExisting(dbLong(COUNT - 1))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ColumnFamily ONE")
        .hasMessageContaining("ahead");
  }

  @Test
  void shouldRejectDeletingKeyAheadOfReverseCursor() {
    // given
    final var cf = filled(Cf.ONE);

    // then
    assertThatThrownBy(
            () ->
                cf.whileTrueReverse(
                    dbLong(COUNT),
                    (key, value) -> {
                      cf.deleteIfExists(dbLong(0));
                      return true;
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ColumnFamily ONE");
  }

  @Test
  void shouldAllowDeletingVisitedAndEarlierKeys() {
    // given
    final var cf = filled(Cf.ONE);

    // when
    final List<Long> visited = new ArrayList<>();
    cf.forEach(
        (key, value) -> {
          visited.add(key.getValue());
          cf.deleteExisting(key);
          if (key.getValue() > 0) {
            cf.deleteIfExists(dbLong(key.getValue() - 1));
          }
        });

    // then
    assertThat(visited).hasSize((int) COUNT);
    assertThat(cf.isEmpty()).isTrue();
  }

  @Test
  void shouldAllowDeletingVisitedAndLaterKeysOfReverseCursor() {
    // given
    final var cf = filled(Cf.ONE);

    // when
    final List<Long> visited = new ArrayList<>();
    cf.whileTrueReverse(
        dbLong(COUNT),
        (key, value) -> {
          visited.add(key.getValue());
          cf.deleteExisting(key);
          cf.deleteIfExists(dbLong(key.getValue() + 1));
          return true;
        });

    // then
    assertThat(visited).hasSize((int) COUNT);
    assertThat(cf.isEmpty()).isTrue();
  }

  @Test
  void shouldAllowDeletingMissingKeyAheadOfCursor() {
    // given
    final var cf = filled(Cf.ONE);

    // then
    assertThatCode(() -> cf.forEach((key, value) -> cf.deleteIfExists(dbLong(COUNT * 10))))
        .doesNotThrowAnyException();
    assertThat(cf.count()).isEqualTo(COUNT);
  }

  @Test
  void shouldAllowDeletingKeyOutsideOfPrefixOfCursor() {
    // given
    final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> cf =
        columnFamily(Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
    for (long i = 0; i < COUNT; i++) {
      cf.insert(longPair(1, i), dbLong(i));
      cf.insert(longPair(2, i), dbLong(i));
    }

    // when
    final var visited = new ArrayList<Long>();
    cf.whileEqualPrefix(
        dbLong(1),
        (key, value) -> {
          visited.add(key.second().getValue());
          cf.deleteExisting(longPair(2, key.second().getValue()));
        });

    // then
    assertThat(visited).hasSize((int) COUNT);
    assertThat(cf.countEqualPrefix(dbLong(2))).isZero();
  }

  @Test
  void shouldRejectDeletingKeyAheadWithinPrefixOfCursor() {
    // given
    final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> cf =
        columnFamily(Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
    for (long i = 0; i < COUNT; i++) {
      cf.insert(longPair(1, i), dbLong(i));
    }

    // then
    assertThatThrownBy(
            () ->
                cf.whileEqualPrefix(
                    dbLong(1),
                    (key, value) -> {
                      cf.deleteExisting(longPair(1, COUNT - 1));
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ColumnFamily ONE");
  }

  @Test
  void shouldAllowDeletingAheadOnceIterationEnded() {
    // given
    final var cf = filled(Cf.ONE);
    cf.forEach((key, value) -> {});

    // then
    assertThatCode(() -> cf.deleteExisting(dbLong(COUNT - 1))).doesNotThrowAnyException();
  }

  private ColumnFamily<DbLong, DbLong> filled(final Cf cf) {
    final var columnFamily = longColumnFamily(cf);
    for (long i = 0; i < COUNT; i++) {
      columnFamily.insert(dbLong(i), dbLong(i));
    }
    return columnFamily;
  }
}
