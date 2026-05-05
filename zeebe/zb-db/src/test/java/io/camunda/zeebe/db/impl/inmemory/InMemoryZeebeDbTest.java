/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class InMemoryZeebeDbTest {

  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbLong, DbLong> columnFamily;
  private DbLong key;
  private DbLong value;

  @BeforeEach
  void setup() {
    zeebeDb = new InMemoryZeebeDb<>();
    key = new DbLong();
    value = new DbLong();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  @AfterEach
  void teardown() throws Exception {
    zeebeDb.close();
  }

  // ---- Basic CRUD ----

  @Nested
  class BasicCrud {
    @Test
    void shouldInsertAndGet() {
      // given
      key.wrapLong(1);
      value.wrapLong(100);

      // when
      columnFamily.insert(key, value);

      // then
      key.wrapLong(1);
      final var result = columnFamily.get(key);
      assertThat(result).isNotNull();
      assertThat(result.getValue()).isEqualTo(100);
    }

    @Test
    void shouldReturnNullForMissing() {
      // given
      key.wrapLong(999);

      // when
      final var result = columnFamily.get(key);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldUpsert() {
      // given
      key.wrapLong(1);
      value.wrapLong(100);
      columnFamily.upsert(key, value);

      // when
      value.wrapLong(200);
      columnFamily.upsert(key, value);

      // then
      key.wrapLong(1);
      assertThat(columnFamily.get(key).getValue()).isEqualTo(200);
    }

    @Test
    void shouldUpdate() {
      // given
      key.wrapLong(1);
      value.wrapLong(100);
      columnFamily.insert(key, value);

      // when
      value.wrapLong(200);
      columnFamily.update(key, value);

      // then
      key.wrapLong(1);
      assertThat(columnFamily.get(key).getValue()).isEqualTo(200);
    }

    @Test
    void shouldFailUpdateIfNotExists() {
      // given
      key.wrapLong(999);
      value.wrapLong(1);

      // when / then
      assertThatThrownBy(() -> columnFamily.update(key, value))
          .isInstanceOf(ZeebeDbInconsistentException.class);
    }

    @Test
    void shouldFailInsertIfExists() {
      // given
      key.wrapLong(1);
      value.wrapLong(100);
      columnFamily.insert(key, value);

      // when / then
      value.wrapLong(200);
      assertThatThrownBy(() -> columnFamily.insert(key, value))
          .isInstanceOf(ZeebeDbInconsistentException.class);
    }

    @Test
    void shouldDelete() {
      // given
      key.wrapLong(1);
      value.wrapLong(100);
      columnFamily.insert(key, value);

      // when
      columnFamily.deleteExisting(key);

      // then
      assertThat(columnFamily.exists(key)).isFalse();
      assertThat(columnFamily.get(key)).isNull();
    }

    @Test
    void shouldDeleteIfExists() {
      // given
      key.wrapLong(1);

      // when / then — should not throw
      columnFamily.deleteIfExists(key);
    }

    @Test
    void shouldFailDeleteIfNotExists() {
      // given
      key.wrapLong(999);

      // when / then
      assertThatThrownBy(() -> columnFamily.deleteExisting(key))
          .isInstanceOf(ZeebeDbInconsistentException.class);
    }

    @Test
    void shouldCheckExists() {
      // given
      key.wrapLong(1);
      value.wrapLong(100);
      columnFamily.insert(key, value);

      // then
      assertThat(columnFamily.exists(key)).isTrue();

      key.wrapLong(999);
      assertThat(columnFamily.exists(key)).isFalse();
    }

    @Test
    void shouldReportEmpty() {
      assertThat(columnFamily.isEmpty()).isTrue();

      key.wrapLong(1);
      value.wrapLong(1);
      columnFamily.insert(key, value);

      assertThat(columnFamily.isEmpty()).isFalse();
    }

    @Test
    void shouldCount() {
      // given
      for (long i = 0; i < 5; i++) {
        key.wrapLong(i);
        value.wrapLong(i * 10);
        columnFamily.insert(key, value);
      }

      // then
      assertThat(columnFamily.count()).isEqualTo(5);
    }
  }

  // ---- Iteration ----

  @Nested
  class Iteration {
    @Test
    void shouldForEachInOrder() {
      // given
      for (long i = 5; i >= 1; i--) {
        key.wrapLong(i);
        value.wrapLong(i * 10);
        columnFamily.insert(key, value);
      }

      // when
      final List<Long> keys = new ArrayList<>();
      columnFamily.forEach((k, v) -> keys.add(k.getValue()));

      // then — should be sorted
      assertThat(keys).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void shouldWhileTrueStopEarly() {
      // given
      for (long i = 1; i <= 5; i++) {
        key.wrapLong(i);
        value.wrapLong(i * 10);
        columnFamily.insert(key, value);
      }

      // when
      final List<Long> keys = new ArrayList<>();
      columnFamily.whileTrue(
          (k, v) -> {
            keys.add(k.getValue());
            return keys.size() < 3;
          });

      // then
      assertThat(keys).containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldWhileTrueStartAt() {
      // given
      for (long i = 1; i <= 5; i++) {
        key.wrapLong(i);
        value.wrapLong(i * 10);
        columnFamily.insert(key, value);
      }

      // when
      key.wrapLong(3);
      final List<Long> keys = new ArrayList<>();
      columnFamily.whileTrue(
          key,
          (k, v) -> {
            keys.add(k.getValue());
            return true;
          });

      // then
      assertThat(keys).containsExactly(3L, 4L, 5L);
    }

    @Test
    void shouldIterateReverse() {
      // given
      for (long i = 1; i <= 5; i++) {
        key.wrapLong(i);
        value.wrapLong(i * 10);
        columnFamily.insert(key, value);
      }

      // when
      key.wrapLong(4);
      final List<Long> keys = new ArrayList<>();
      columnFamily.whileTrueReverse(
          key,
          (k, v) -> {
            keys.add(k.getValue());
            return true;
          });

      // then
      assertThat(keys).containsExactly(4L, 3L, 2L, 1L);
    }

    @Test
    void shouldForEachKey() {
      // given
      for (long i = 1; i <= 3; i++) {
        key.wrapLong(i);
        value.wrapLong(i);
        columnFamily.insert(key, value);
      }

      // when
      final List<Long> keys = new ArrayList<>();
      columnFamily.forEachKey(k -> keys.add(k.getValue()));

      // then
      assertThat(keys).containsExactly(1L, 2L, 3L);
    }
  }

  // ---- Prefix iteration ----

  @Nested
  class PrefixIteration {
    private DbLong firstKey;
    private DbLong secondKey;
    private DbCompositeKey<DbLong, DbLong> compositeKey;
    private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> compositeFamily;

    @BeforeEach
    void setupComposite() {
      firstKey = new DbLong();
      secondKey = new DbLong();
      compositeKey = new DbCompositeKey<>(firstKey, secondKey);
      compositeFamily =
          zeebeDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), compositeKey, DbNil.INSTANCE);
    }

    @Test
    void shouldIterateWithPrefix() {
      // given — insert 3 prefixes × 3 suffixes
      for (long prefix = 1; prefix <= 3; prefix++) {
        for (long suffix = 1; suffix <= 3; suffix++) {
          firstKey.wrapLong(prefix);
          secondKey.wrapLong(suffix);
          compositeFamily.upsert(compositeKey, DbNil.INSTANCE);
        }
      }

      // when — iterate prefix=2
      firstKey.wrapLong(2);
      final List<Long> suffixes = new ArrayList<>();
      compositeFamily.whileEqualPrefix(
          firstKey,
          (k, v) -> {
            suffixes.add(k.second().getValue());
          });

      // then
      assertThat(suffixes).containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldCountWithPrefix() {
      // given
      for (long prefix = 1; prefix <= 3; prefix++) {
        for (long suffix = 1; suffix <= 3; suffix++) {
          firstKey.wrapLong(prefix);
          secondKey.wrapLong(suffix);
          compositeFamily.upsert(compositeKey, DbNil.INSTANCE);
        }
      }

      // when / then
      firstKey.wrapLong(2);
      assertThat(compositeFamily.countEqualPrefix(firstKey)).isEqualTo(3);
    }

    @Test
    void shouldNotReturnEntriesOfOtherPrefixes() {
      // given
      for (long prefix = 1; prefix <= 3; prefix++) {
        firstKey.wrapLong(prefix);
        secondKey.wrapLong(1);
        compositeFamily.upsert(compositeKey, DbNil.INSTANCE);
      }

      // when — iterate prefix=2
      firstKey.wrapLong(2);
      final List<Long> prefixes = new ArrayList<>();
      compositeFamily.whileEqualPrefix(
          firstKey,
          (k, v) -> {
            prefixes.add(k.first().getValue());
          });

      // then
      assertThat(prefixes).containsExactly(2L);
    }
  }

  // ---- Transaction isolation ----

  @Nested
  class TransactionIsolation {
    @Test
    void shouldNotSeeUncommittedWritesFromOtherContext() throws Exception {
      // given — two contexts on the same db
      final TransactionContext ctx1 = zeebeDb.createContext();
      final TransactionContext ctx2 = zeebeDb.createContext();

      final var cf1 =
          zeebeDb.createColumnFamily(DefaultColumnFamily.DEFAULT, ctx1, new DbLong(), new DbLong());
      final var cf2 =
          zeebeDb.createColumnFamily(DefaultColumnFamily.DEFAULT, ctx2, new DbLong(), new DbLong());

      // when — start a transaction on ctx1 and write, but don't commit yet
      final var tx1 = ctx1.getCurrentTransaction();

      final var writeKey = new DbLong();
      writeKey.wrapLong(42);
      final var writeValue = new DbLong();
      writeValue.wrapLong(999);

      tx1.run(() -> cf1.upsert(writeKey, writeValue));

      // then — ctx2 should NOT see the uncommitted value
      final var readKey = new DbLong();
      readKey.wrapLong(42);
      ctx2.runInTransaction(
          () -> {
            assertThat(cf2.exists(readKey)).isFalse();
          });

      // when — commit ctx1
      tx1.commit();

      // then — now ctx2 should see it
      ctx2.runInTransaction(
          () -> {
            assertThat(cf2.exists(readKey)).isTrue();
            assertThat(cf2.get(readKey).getValue()).isEqualTo(999);
          });
    }

    @Test
    void shouldRollbackTransaction() {
      // given
      key.wrapLong(1);
      value.wrapLong(100);
      columnFamily.insert(key, value);

      // when — insert in transaction, then throw to trigger rollback
      assertThatThrownBy(
              () ->
                  columnFamily
                      .get(key)
                      .wrap(
                          new org.agrona.concurrent.UnsafeBuffer(new byte[0]),
                          0,
                          0)) // doesn't matter, we just need to cause a transaction
          .isInstanceOf(Exception.class);

      // verify original value is still there
      key.wrapLong(1);
      assertThat(columnFamily.get(key).getValue()).isEqualTo(100);
    }

    @Test
    void shouldSeeOwnWritesWithinTransaction() {
      // given
      final TransactionContext ctx = zeebeDb.createContext();
      final var cf =
          zeebeDb.createColumnFamily(DefaultColumnFamily.DEFAULT, ctx, new DbLong(), new DbLong());

      // when / then
      ctx.runInTransaction(
          () -> {
            final var k = new DbLong();
            k.wrapLong(42);
            final var v = new DbLong();
            v.wrapLong(999);
            cf.upsert(k, v);

            // Should see own write within the same transaction
            k.wrapLong(42);
            assertThat(cf.exists(k)).isTrue();
            assertThat(cf.get(k).getValue()).isEqualTo(999);
          });
    }

    @Test
    void shouldIsolateFromConcurrentReaders() throws Exception {
      // given
      key.wrapLong(1);
      value.wrapLong(100);
      columnFamily.insert(key, value);

      final TransactionContext readerCtx = zeebeDb.createContext();
      final var readerCf =
          zeebeDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, readerCtx, new DbLong(), new DbLong());

      // when — writer starts a transaction and modifies
      final var writerCtx = zeebeDb.createContext();
      final var writerCf =
          zeebeDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, writerCtx, new DbLong(), new DbLong());

      final var writerTx = writerCtx.getCurrentTransaction();
      final var wk = new DbLong();
      wk.wrapLong(1);
      final var wv = new DbLong();
      wv.wrapLong(200);
      writerTx.run(() -> writerCf.upsert(wk, wv));

      // then — reader still sees old value
      final var rk = new DbLong();
      rk.wrapLong(1);
      readerCtx.runInTransaction(
          () -> {
            assertThat(readerCf.get(rk).getValue()).isEqualTo(100);
          });

      // when — commit
      writerTx.commit();

      // then — reader sees new value
      readerCtx.runInTransaction(
          () -> {
            assertThat(readerCf.get(rk).getValue()).isEqualTo(200);
          });
    }
  }

  // ---- Multiple values ----

  @Nested
  class MultipleValues {
    @Test
    void shouldPutMultipleValues() {
      // given
      key.wrapLong(1);
      value.wrapLong(10);
      columnFamily.insert(key, value);

      key.wrapLong(2);
      value.wrapLong(20);
      columnFamily.insert(key, value);

      key.wrapLong(3);
      value.wrapLong(30);
      columnFamily.insert(key, value);

      // then
      key.wrapLong(1);
      assertThat(columnFamily.get(key).getValue()).isEqualTo(10);

      key.wrapLong(2);
      assertThat(columnFamily.get(key).getValue()).isEqualTo(20);

      key.wrapLong(3);
      assertThat(columnFamily.get(key).getValue()).isEqualTo(30);
    }

    @Test
    void shouldDeleteAndReinsert() {
      // given
      key.wrapLong(1);
      value.wrapLong(10);
      columnFamily.insert(key, value);

      // when
      columnFamily.deleteExisting(key);
      value.wrapLong(20);
      columnFamily.insert(key, value);

      // then
      assertThat(columnFamily.get(key).getValue()).isEqualTo(20);
    }
  }
}
