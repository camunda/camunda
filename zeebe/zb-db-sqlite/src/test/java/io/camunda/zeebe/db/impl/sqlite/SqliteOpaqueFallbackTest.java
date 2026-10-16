/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine uses one column family with an old and a new key shape while it migrates, and RocksDB
 * does not care. A table with typed columns does, so it falls back to one opaque key column.
 */
final class SqliteOpaqueFallbackTest extends ConformanceDbTestBase {

  @TempDir Path snapshotParent;

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>();
  }

  @Test
  void shouldKeepRowsWrittenBeforeTheSecondKeyShapeArrived() {
    // given a typed table with rows
    final var legacy = longColumnFamily(Cf.ONE);
    legacy.insert(dbLong(3), dbLong(30));
    legacy.insert(dbLong(1), dbLong(10));
    legacy.insert(dbLong(-5), dbLong(-50));

    // when the same column family is used with a different key shape, a foreign key makes its
    // numeric component an INTEGER
    final var current =
        columnFamily(Cf.ONE, new DbForeignKey<>(new DbLong(), Cf.TWO), new DbLong());

    // then both see all rows, in the order of the serialized keys
    assertThat(legacy.get(dbLong(1)).getValue()).isEqualTo(10);
    assertThat(current.get(new DbForeignKey<>(dbLong(3), Cf.TWO)).getValue()).isEqualTo(30);
    assertThat(keys(legacy)).containsExactly(1L, 3L, -5L);
    assertThat(legacy.count()).isEqualTo(3);
  }

  @Test
  void shouldShareRowsBetweenBothKeyShapesAfterwards() {
    // given
    final var legacy = longColumnFamily(Cf.ONE);
    final var current =
        columnFamily(Cf.ONE, new DbForeignKey<>(new DbLong(), Cf.TWO), new DbLong());

    // when
    legacy.insert(dbLong(-1), dbLong(1));
    current.insert(new DbForeignKey<>(dbLong(7), Cf.TWO), dbLong(70));

    // then
    assertThat(legacy.get(dbLong(7)).getValue()).isEqualTo(70);
    assertThat(current.exists(new DbForeignKey<>(dbLong(7), Cf.TWO))).isTrue();
    assertThat(keys(legacy)).containsExactly(7L, -1L);
  }

  @Test
  void shouldScanPrefixesOfOpaqueKeysIncludingOnesOfOnlyMaxBytes() {
    // given a composite key that is used with a second shape afterwards
    final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> pairs =
        columnFamily(Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
    pairs.insert(longPair(1, 1), dbLong(0));
    pairs.insert(longPair(1, 2), dbLong(0));
    pairs.insert(longPair(2, 1), dbLong(0));
    pairs.insert(longPair(-1, 1), dbLong(0));
    pairs.insert(longPair(-1, 2), dbLong(0));
    columnFamily(Cf.ONE, new DbCompositeKey<>(new DbString(), new DbLong()), new DbLong());

    // when
    final List<Long> one = new ArrayList<>();
    pairs.whileEqualPrefix(
        dbLong(1),
        (key, value) -> {
          one.add(key.second().getValue());
        });
    final List<Long> negative = new ArrayList<>();
    pairs.whileEqualPrefix(
        dbLong(-1),
        (key, value) -> {
          negative.add(key.second().getValue());
        });
    final List<Long> startingAtSecond = new ArrayList<>();
    pairs.whileEqualPrefix(
        dbLong(-1),
        longPair(-1, 2),
        (key, value) -> {
          startingAtSecond.add(key.second().getValue());
          return true;
        });

    // then
    assertThat(one).containsExactly(1L, 2L);
    assertThat(negative).containsExactly(1L, 2L);
    assertThat(startingAtSecond).containsExactly(2L);
  }

  @Test
  void shouldUseOpaqueTableOfEarlierSessionWithoutFurtherChanges() throws Exception {
    // given
    final var legacy = longColumnFamily(Cf.ONE);
    legacy.insert(dbLong(4), dbLong(40));
    columnFamily(Cf.ONE, new DbForeignKey<>(new DbLong(), Cf.TWO), new DbLong());
    final var snapshotDir = new File(snapshotParent.toFile(), "snapshot");
    db.createSnapshot(snapshotDir);

    // when
    try (final var snapshot = this.<Cf>factory().createDb(snapshotDir)) {
      final var context = snapshot.createContext();
      final var restored = snapshot.createColumnFamily(Cf.ONE, context, new DbLong(), new DbLong());
      final var restoredCurrent =
          snapshot.createColumnFamily(
              Cf.ONE, context, new DbForeignKey<>(new DbLong(), Cf.TWO), new DbLong());

      // then
      assertThat(restored.get(dbLong(4)).getValue()).isEqualTo(40);
      assertThat(restoredCurrent.exists(new DbForeignKey<>(dbLong(4), Cf.TWO))).isTrue();
      assertThat(keys(restored)).containsExactly(4L);
    }
  }

  @Test
  void shouldRejectTypedTableOfEarlierSessionForDifferentKeyShape() throws Exception {
    // given
    longColumnFamily(Cf.ONE);
    final var snapshotDir = new File(snapshotParent.toFile(), "snapshot");
    db.createSnapshot(snapshotDir);

    // then the rows were written with a layout that is no longer known, so they can not be rebuilt
    try (final var snapshot = this.<Cf>factory().createDb(snapshotDir)) {
      assertThatThrownBy(
              () ->
                  snapshot.createColumnFamily(
                      Cf.ONE,
                      snapshot.createContext(),
                      new DbForeignKey<>(new DbLong(), Cf.TWO),
                      new DbLong()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("same key structure");
    }
  }

  private static List<Long> keys(final ColumnFamily<DbLong, DbLong> columnFamily) {
    final List<Long> keys = new ArrayList<>();
    columnFamily.forEachKey(key -> keys.add(key.getValue()));
    return keys;
  }
}
