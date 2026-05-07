/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contract test suite for all ZeebeDb implementations. Subclasses supply a factory via {@link
 * #factory()} and the suite verifies that the implementation honours the full column-family and
 * transaction API contract.
 */
abstract class ZeebeDbContractTest {

  @TempDir File tmpDir;

  ZeebeDb<Families> db;
  TransactionContext ctx;

  ColumnFamily<DbLong, DbLong> cf;
  ColumnFamily<DbLong, DbLong> cf2;
  ColumnFamily<DbLong, DbLong> cf3;

  DbLong key;
  DbLong value;
  DbLong key2;
  DbLong value2;
  DbLong key3;
  DbLong value3;

  abstract ZeebeDbFactory<Families> factory();

  @BeforeEach
  void setUp() {
    db = factory().createDb(tmpDir);
    ctx = db.createContext();

    key = new DbLong();
    value = new DbLong();
    cf = db.createColumnFamily(Families.ONE, ctx, key, value);

    key2 = new DbLong();
    value2 = new DbLong();
    cf2 = db.createColumnFamily(Families.TWO, ctx, key2, value2);

    key3 = new DbLong();
    value3 = new DbLong();
    cf3 = db.createColumnFamily(Families.THREE, ctx, key3, value3);
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  // ---- Basic CRUD ----

  @Test
  void shouldInsertAndGetValue() {
    // given
    key.wrapLong(1);
    value.wrapLong(42);
    cf.insert(key, value);

    // when
    value.wrapLong(0);
    final DbLong result = cf.get(key);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getValue()).isEqualTo(42);
  }

  @Test
  void shouldReturnNullForMissingKey() {
    key.wrapLong(999);
    assertThat(cf.get(key)).isNull();
  }

  @Test
  void shouldUpdateValue() {
    // given
    key.wrapLong(1);
    value.wrapLong(10);
    cf.insert(key, value);

    // when
    value.wrapLong(20);
    cf.update(key, value);
    value.wrapLong(0);

    // then
    assertThat(cf.get(key).getValue()).isEqualTo(20);
  }

  @Test
  void shouldUpdateValueWithMutator() {
    // given
    key.wrapLong(1);
    value.wrapLong(10);
    cf.insert(key, value);

    // when
    final var updatedValue =
        cf.updateAndGet(
            key,
            storedValue -> {
              storedValue.wrapLong(storedValue.getValue() + 10);
              return storedValue.getValue();
            });

    // then
    assertThat(updatedValue).isEqualTo(20L);
    assertThat(cf.get(key).getValue()).isEqualTo(20L);
  }

  @Test
  void shouldUpsertMissingValueWithMutatorSupplier() {
    // given
    key.wrapLong(1);

    // when
    final var updatedValue =
        cf.updateAndGet(
            key,
            DbLong::new,
            storedValue -> {
              storedValue.wrapLong(storedValue.getValue() + 10);
              return storedValue.getValue();
            });

    // then
    assertThat(updatedValue).isEqualTo(10L);
    assertThat(cf.get(key).getValue()).isEqualTo(10L);
  }

  @Test
  void shouldUpdateValueWithConsumerMutator() {
    // given
    key.wrapLong(1);
    value.wrapLong(10);
    cf.insert(key, value);

    // when
    cf.update(key, storedValue -> storedValue.wrapLong(storedValue.getValue() + 5));

    // then
    assertThat(cf.get(key).getValue()).isEqualTo(15L);
  }

  @Test
  void shouldThrowOnMutatorUpdateMissingKeyWithoutSupplier() {
    // given
    key.wrapLong(1);

    // when / then
    assertThatThrownBy(() -> cf.update(key, storedValue -> storedValue.wrapLong(10)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void shouldUpsertNewAndExistingValue() {
    key.wrapLong(1);
    value.wrapLong(1);
    cf.upsert(key, value);

    value.wrapLong(2);
    cf.upsert(key, value);

    assertThat(cf.get(key).getValue()).isEqualTo(2);
  }

  @Test
  void shouldDeleteExistingKey() {
    // given
    key.wrapLong(1);
    value.wrapLong(10);
    cf.insert(key, value);

    // when
    cf.deleteExisting(key);

    // then
    assertThat(cf.exists(key)).isFalse();
    assertThat(cf.get(key)).isNull();
  }

  @Test
  void shouldNotAffectOtherKeysOnDelete() {
    key.wrapLong(1);
    value.wrapLong(10);
    cf.insert(key, value);

    key.wrapLong(2);
    value.wrapLong(20);
    cf.insert(key, value);

    key.wrapLong(1);
    cf.deleteExisting(key);

    key.wrapLong(2);
    assertThat(cf.get(key).getValue()).isEqualTo(20);
  }

  @Test
  void shouldReportExistenceCorrectly() {
    key.wrapLong(1);
    assertThat(cf.exists(key)).isFalse();

    value.wrapLong(10);
    cf.insert(key, value);
    assertThat(cf.exists(key)).isTrue();

    cf.deleteExisting(key);
    assertThat(cf.exists(key)).isFalse();
  }

  @Test
  void shouldReportIsEmpty() {
    assertThat(cf.isEmpty()).isTrue();

    key.wrapLong(1);
    value.wrapLong(10);
    cf.upsert(key, value);
    assertThat(cf.isEmpty()).isFalse();

    cf.deleteExisting(key);
    assertThat(cf.isEmpty()).isTrue();
  }

  @Test
  void shouldThrowOnDuplicateInsert() {
    key.wrapLong(1);
    value.wrapLong(10);
    cf.insert(key, value);

    assertThatThrownBy(() -> cf.insert(key, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void shouldThrowOnUpdateMissingKey() {
    key.wrapLong(1);
    value.wrapLong(10);

    assertThatThrownBy(() -> cf.update(key, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void shouldThrowOnDeleteExistingMissingKey() {
    key.wrapLong(1);
    assertThatThrownBy(() -> cf.deleteExisting(key))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
  }

  // ---- get() value-instance contract ----

  @Test
  void shouldReturnRegisteredValueInstanceOnGet() {
    // given
    key.wrapLong(1);
    value.wrapLong(42);
    cf.insert(key, value);

    // when
    value.wrapLong(0);
    final DbLong result = cf.get(key);

    // then — result is the registered value instance (same reference), populated with the stored
    // data
    assertThat(result).isSameAs(value);
    assertThat(result.getValue()).isEqualTo(42);
  }

  @Test
  void shouldReturnIndependentInstanceFromSupplierGet() {
    // given
    key.wrapLong(1);
    value.wrapLong(42);
    cf.insert(key, value);

    // when
    final DbLong r1 = cf.get(key, DbLong::new);
    final DbLong r2 = cf.get(key, DbLong::new);

    // then — each supplier call returns a distinct instance
    assertThat(r1).isNotSameAs(r2);
    assertThat(r1.getValue()).isEqualTo(42);
    assertThat(r2.getValue()).isEqualTo(42);
  }

  @Test
  void shouldPreserveSupplierValueAcrossSubsequentGet() {
    // given
    key.wrapLong(1);
    value.wrapLong(10);
    cf.upsert(key, value);
    key.wrapLong(2);
    value.wrapLong(20);
    cf.upsert(key, value);

    // when
    key.wrapLong(1);
    final DbLong r1 = cf.get(key, DbLong::new);
    key.wrapLong(2);
    final DbLong r2 = cf.get(key, DbLong::new);

    // then — r1 is not affected by the second get
    assertThat(r1.getValue()).isEqualTo(10);
    assertThat(r2.getValue()).isEqualTo(20);
  }

  // ---- Iteration ----

  @Test
  void shouldForEachInKeyOrder() {
    // given
    upsert(4567, 1);
    upsert(1213, 2);
    upsert(1, 3);

    // when
    final List<Long> keys = new ArrayList<>();
    cf.forEach((k, v) -> keys.add(k.getValue()));

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L);
  }

  @Test
  void shouldWhileTrueStopEarly() {
    // given
    upsert(1, 10);
    upsert(2, 20);
    upsert(3, 30);

    // when
    final List<Long> keys = new ArrayList<>();
    cf.whileTrue(
        (k, v) -> {
          keys.add(k.getValue());
          return k.getValue() != 2;
        });

    // then
    assertThat(keys).containsExactly(1L, 2L);
  }

  @Test
  void shouldWhileTrueReverseStopEarly() {
    // given
    upsert(1, 10);
    upsert(2, 20);
    upsert(3, 30);

    final var startAt = new DbLong();
    startAt.wrapLong(3);

    // when
    final List<Long> keys = new ArrayList<>();
    cf.whileTrueReverse(
        startAt,
        (k, v) -> {
          keys.add(k.getValue());
          return k.getValue() != 2;
        });

    // then
    assertThat(keys).containsExactly(3L, 2L);
  }

  @Test
  void shouldDeleteDuringForEach() {
    upsert(1, 10);
    upsert(2, 20);
    upsert(3, 30);

    cf.forEach((k, v) -> cf.deleteExisting(k));

    assertThat(cf.isEmpty()).isTrue();
  }

  // ---- Transaction semantics ----

  @Test
  void shouldCommitMultiCfTransaction() {
    // given
    key.wrapLong(1);
    value.wrapLong(10);
    key2.wrapLong(2);
    value2.wrapLong(20);

    // when
    ctx.runInTransaction(
        () -> {
          cf.insert(key, value);
          cf2.insert(key2, value2);
        });

    // then
    assertThat(cf.exists(key)).isTrue();
    assertThat(cf2.exists(key2)).isTrue();
  }

  @Test
  void shouldRollbackOnException() {
    key.wrapLong(1);
    value.wrapLong(10);
    key2.wrapLong(2);
    value2.wrapLong(20);

    try {
      ctx.runInTransaction(
          () -> {
            cf.insert(key, value);
            cf2.insert(key2, value2);
            throw new RuntimeException("abort");
          });
    } catch (final RuntimeException ignored) {
    }

    assertThat(cf.exists(key)).isFalse();
    assertThat(cf2.exists(key2)).isFalse();
  }

  @Test
  void shouldNotWrapRuntimeExceptionFromTransaction() {
    final var cause = new RuntimeException("original");
    assertThatThrownBy(
            () ->
                ctx.runInTransaction(
                    () -> {
                      throw cause;
                    }))
        .isSameAs(cause);
  }

  @Test
  void shouldReadOwnWritesWithinTransaction() {
    // given
    key.wrapLong(1);
    value.wrapLong(10);

    final long[] seen = {0};
    ctx.runInTransaction(
        () -> {
          cf.insert(key, value);
          seen[0] = cf.get(key).getValue();
        });

    assertThat(seen[0]).isEqualTo(10);
  }

  @Test
  void shouldSeeCommittedDataInNextTransaction() {
    // given — write and commit in first transaction
    key.wrapLong(1);
    value.wrapLong(99);
    ctx.runInTransaction(() -> cf.insert(key, value));

    // when — read in a separate transaction
    value.wrapLong(0);
    final long[] seen = {0};
    ctx.runInTransaction(() -> seen[0] = cf.get(key).getValue());

    // then
    assertThat(seen[0]).isEqualTo(99);
  }

  @Test
  void shouldSeeCommittedDataFromGetAfterTransaction() {
    // given — write and commit
    key.wrapLong(42);
    value.wrapLong(7);
    ctx.runInTransaction(() -> cf.upsert(key, value));

    // when — plain get outside of explicit transaction wrapping (auto-transaction)
    value.wrapLong(0);
    final DbLong result = cf.get(key);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getValue()).isEqualTo(7);
  }

  @Test
  void shouldSeeDeleteInNextTransaction() {
    key.wrapLong(1);
    value.wrapLong(10);
    ctx.runInTransaction(() -> cf.insert(key, value));

    ctx.runInTransaction(() -> cf.deleteExisting(key));

    assertThat(cf.exists(key)).isFalse();
  }

  @Test
  void shouldHandleWriteThenDeleteInSameTransaction() {
    key.wrapLong(1);
    value.wrapLong(10);

    ctx.runInTransaction(
        () -> {
          cf.insert(key, value);
          cf.deleteExisting(key);
        });

    assertThat(cf.exists(key)).isFalse();
  }

  @Test
  void shouldSeeUpdateAcrossTransactions() {
    key.wrapLong(1);
    value.wrapLong(1);
    ctx.runInTransaction(() -> cf.insert(key, value));

    value.wrapLong(2);
    ctx.runInTransaction(() -> cf.update(key, value));

    value.wrapLong(0);
    assertThat(cf.get(key).getValue()).isEqualTo(2);
  }

  @Test
  void shouldNestTransactionsWithoutDoubleCommit() {
    key.wrapLong(1);
    value.wrapLong(10);

    ctx.runInTransaction(
        () -> {
          ctx.runInTransaction(() -> cf.insert(key, value));
          // still within the outer transaction — key must be visible
          assertThat(cf.exists(key)).isTrue();
        });

    assertThat(cf.exists(key)).isTrue();
  }

  // ---- Multi-iteration with mixed committed/pending state ----

  @Test
  void shouldIterateAcrossCommittedAndPendingWrites() {
    upsert(1, 10);
    upsert(3, 30);

    final Map<Long, Long> seen = new HashMap<>();
    ctx.runInTransaction(
        () -> {
          key.wrapLong(2);
          value.wrapLong(20);
          cf.insert(key, value);
          cf.forEach((k, v) -> seen.put(k.getValue(), v.getValue()));
        });

    assertThat(seen).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 10L, 2L, 20L, 3L, 30L));
  }

  // ---- Helpers ----

  private void upsert(final long k, final long v) {
    key.wrapLong(k);
    value.wrapLong(v);
    cf.upsert(key, value);
  }

  enum Families implements EnumValue, ScopedColumnFamily {
    DEFAULT,
    ONE,
    TWO,
    THREE;

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
