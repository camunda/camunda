/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.PerfLevel;
import org.rocksdb.RocksDB;

final class PrefixBoundedIterationTest {

  private ZeebeDb<TestColumnFamily> zeebeDb;
  private DbLong firstKey;
  private DbLong secondKey;
  private DbCompositeKey<DbLong, DbLong> compositeKey;
  private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> columnFamily;

  @BeforeEach
  void setup(final @TempDir File pathName) {
    zeebeDb = DefaultZeebeDbFactory.<TestColumnFamily>getDefaultFactory().createDb(pathName);
    firstKey = new DbLong();
    secondKey = new DbLong();
    compositeKey = new DbCompositeKey<>(firstKey, secondKey);
    columnFamily =
        zeebeDb.createColumnFamily(
            TestColumnFamily.DEFAULT, zeebeDb.createContext(), compositeKey, DbNil.INSTANCE);
  }

  @AfterEach
  void tearDown() throws Exception {
    zeebeDb.close();
  }

  @Test
  void shouldNotSkipTombstonesBeyondTheIteratedPrefix() throws Exception {
    // given - many tombstones right after the (empty) prefix 1, and a live key after them
    for (long suffix = 0; suffix < 1_000; suffix++) {
      insert(2, suffix);
    }
    for (long suffix = 0; suffix < 1_000; suffix++) {
      firstKey.wrapLong(2);
      secondKey.wrapLong(suffix);
      columnFamily.deleteExisting(compositeKey);
    }
    insert(3, 0);

    final RocksDB rocksDb = rocksDb();
    rocksDb.setPerfLevel(PerfLevel.ENABLE_COUNT);
    rocksDb.getPerfContext().reset();

    // when
    final var visited = visitPrefix(1);

    // then
    assertThat(visited).isEmpty();
    assertThat(rocksDb.getPerfContext().getInternalDeleteSkippedCount()).isZero();
  }

  @Test
  void shouldIteratePrefixWithoutSuccessorInItsLastBytes() {
    // given - -1 is all 0xFF bytes, so the bound has to carry into the column family prefix
    insert(5, 1);
    insert(-1, 1);
    insert(-1, 2);
    insert(Long.MIN_VALUE, 1);

    // when
    final var visited = visitPrefix(-1);

    // then
    assertThat(visited).containsExactly(List.of(-1L, 1L), List.of(-1L, 2L));
  }

  @Test
  void shouldBoundNestedIterationsIndependently() {
    // given
    insert(1, 1);
    insert(1, 2);
    insert(2, 1);
    insert(3, 1);
    final var outerPrefix = new DbLong();
    final var innerPrefix = new DbLong();
    final List<List<Long>> visited = new ArrayList<>();

    // when - the inner iteration reuses the column family while the outer iterator is open
    outerPrefix.wrapLong(1);
    columnFamily.whileEqualPrefix(
        outerPrefix,
        (outerKey, outerValue) -> {
          final long outerSecond = outerKey.second().getValue();
          innerPrefix.wrapLong(2);
          columnFamily.whileEqualPrefix(
              innerPrefix,
              (innerKey, innerValue) -> {
                visited.add(
                    List.of(
                        outerSecond, innerKey.first().getValue(), innerKey.second().getValue()));
              });
        });

    // then
    assertThat(visited).containsExactly(List.of(1L, 2L, 1L), List.of(2L, 2L, 1L));
  }

  @Test
  void shouldIterateInReverseWithinTheColumnFamily() {
    // given
    insert(1, 1);
    insert(2, 1);
    insert(3, 1);

    // when
    final List<List<Long>> visited = new ArrayList<>();
    firstKey.wrapLong(2);
    secondKey.wrapLong(1);
    columnFamily.whileTrueReverse(
        compositeKey,
        (key, value) -> {
          visited.add(List.of(key.first().getValue(), key.second().getValue()));
          return true;
        });

    // then
    assertThat(visited).containsExactly(List.of(2L, 1L), List.of(1L, 1L));
  }

  @Test
  void shouldSeeUncommittedWritesWithinPrefix() {
    // given
    insert(1, 1);

    // when - inserts and iteration happen in the same transaction, so the batch side is bounded too
    final List<List<Long>> visited = new ArrayList<>();
    final var context = zeebeDb.createContext();
    final var transactionalColumnFamily =
        zeebeDb.createColumnFamily(TestColumnFamily.DEFAULT, context, compositeKey, DbNil.INSTANCE);
    context.runInTransaction(
        () -> {
          firstKey.wrapLong(1);
          secondKey.wrapLong(2);
          transactionalColumnFamily.upsert(compositeKey, DbNil.INSTANCE);
          firstKey.wrapLong(2);
          secondKey.wrapLong(1);
          transactionalColumnFamily.upsert(compositeKey, DbNil.INSTANCE);

          final var prefix = new DbLong();
          prefix.wrapLong(1);
          transactionalColumnFamily.whileEqualPrefix(
              prefix,
              (key, value) -> {
                visited.add(List.of(key.first().getValue(), key.second().getValue()));
              });
        });

    // then
    assertThat(visited).containsExactly(List.of(1L, 1L), List.of(1L, 2L));
  }

  @Test
  void shouldIterateForwardFromStartAtWithinPrefix() {
    // given
    insert(1, 1);
    insert(1, 2);
    insert(1, 3);
    insert(2, 1);

    // when
    final var prefix = new DbLong();
    prefix.wrapLong(1);
    firstKey.wrapLong(1);
    secondKey.wrapLong(2);
    final List<List<Long>> visited = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        prefix,
        compositeKey,
        (key, value) -> {
          visited.add(List.of(key.first().getValue(), key.second().getValue()));
          return true;
        });

    // then
    assertThat(visited).containsExactly(List.of(1L, 2L), List.of(1L, 3L));
  }

  @Test
  void shouldIterateInReverseFromStartAtWithinColumnFamily() {
    // given
    insert(1, 1);
    insert(1, 2);
    insert(1, 3);

    // when
    firstKey.wrapLong(1);
    secondKey.wrapLong(2);
    final List<List<Long>> visited = new ArrayList<>();
    columnFamily.whileTrueReverse(
        compositeKey,
        (key, value) -> {
          visited.add(List.of(key.first().getValue(), key.second().getValue()));
          return true;
        });

    // then
    assertThat(visited).containsExactly(List.of(1L, 2L), List.of(1L, 1L));
  }

  @Test
  void shouldNotVisitUncommittedKeysOutsidePrefixWhenIteratingForward() {
    // given
    final var context = zeebeDb.createContext();
    final var transactionalColumnFamily =
        zeebeDb.createColumnFamily(TestColumnFamily.DEFAULT, context, compositeKey, DbNil.INSTANCE);
    final List<List<Long>> visited = new ArrayList<>();

    // when - keys just below and above the prefix are written in the same, still open transaction
    context.runInTransaction(
        () -> {
          upsert(transactionalColumnFamily, 0, 9);
          upsert(transactionalColumnFamily, 1, 1);
          upsert(transactionalColumnFamily, 1, 2);
          upsert(transactionalColumnFamily, 2, 0);

          final var prefix = new DbLong();
          prefix.wrapLong(1);
          firstKey.wrapLong(1);
          secondKey.wrapLong(1);
          transactionalColumnFamily.whileEqualPrefix(
              prefix,
              compositeKey,
              (key, value) -> {
                visited.add(List.of(key.first().getValue(), key.second().getValue()));
                return true;
              });
        });

    // then
    assertThat(visited).containsExactly(List.of(1L, 1L), List.of(1L, 2L));
  }

  @Test
  void shouldNotVisitUncommittedKeysOutsideColumnFamilyWhenIteratingInReverse() {
    // given - the neighbouring column families sit right below and above the iterated one
    final var context = zeebeDb.createContext();
    final var below =
        zeebeDb.createColumnFamily(TestColumnFamily.DEFAULT, context, compositeKey, DbNil.INSTANCE);
    final var target =
        zeebeDb.createColumnFamily(TestColumnFamily.TARGET, context, compositeKey, DbNil.INSTANCE);
    final var above =
        zeebeDb.createColumnFamily(TestColumnFamily.ABOVE, context, compositeKey, DbNil.INSTANCE);
    final List<List<Long>> visited = new ArrayList<>();

    // when
    context.runInTransaction(
        () -> {
          upsert(below, 9, 9);
          upsert(target, 1, 1);
          upsert(target, 1, 2);
          upsert(above, 0, 0);

          firstKey.wrapLong(Long.MAX_VALUE);
          secondKey.wrapLong(Long.MAX_VALUE);
          target.whileTrueReverse(
              compositeKey,
              (key, value) -> {
                visited.add(List.of(key.first().getValue(), key.second().getValue()));
                return true;
              });
        });

    // then
    assertThat(visited).containsExactly(List.of(1L, 2L), List.of(1L, 1L));
  }

  private void upsert(
      final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> family,
      final long first,
      final long second) {
    firstKey.wrapLong(first);
    secondKey.wrapLong(second);
    family.upsert(compositeKey, DbNil.INSTANCE);
  }

  private void insert(final long first, final long second) {
    firstKey.wrapLong(first);
    secondKey.wrapLong(second);
    columnFamily.upsert(compositeKey, DbNil.INSTANCE);
  }

  private List<List<Long>> visitPrefix(final long prefixValue) {
    final var prefix = new DbLong();
    prefix.wrapLong(prefixValue);
    final List<List<Long>> visited = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        prefix,
        (key, value) -> {
          visited.add(List.of(key.first().getValue(), key.second().getValue()));
        });
    return visited;
  }

  private RocksDB rocksDb() throws ReflectiveOperationException {
    final var field = ZeebeTransactionDb.class.getDeclaredField("rocksDB");
    field.setAccessible(true);
    return (RocksDB) field.get(zeebeDb);
  }

  /** Three adjacent column families, so the ones next to {@code TARGET} can be written to. */
  private enum TestColumnFamily implements EnumValue, ScopedColumnFamily {
    DEFAULT,
    TARGET,
    ABOVE;

    @Override
    public int getValue() {
      return ordinal();
    }

    @Override
    public ColumnFamilyScope partitionScope() {
      return ColumnFamilyScope.PARTITION_LOCAL;
    }
  }
}
