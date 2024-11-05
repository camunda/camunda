/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class InMemoryZeebeDbTransactionTest {

  private final InMemoryDbFactory<ColumnFamilies> dbFactory = new InMemoryDbFactory<>();

  private TransactionContext transactionContext;

  private ColumnFamily<DbLong, DbLong> oneColumnFamily;
  private ColumnFamily<DbLong, DbLong> twoColumnFamily;
  private ColumnFamily<DbLong, DbLong> threeColumnFamily;

  private DbLong oneKey;
  private DbLong oneValue;
  private DbLong twoValue;
  private DbLong twoKey;
  private DbLong threeKey;
  private DbLong threeValue;

  @BeforeEach
  void setup() throws Exception {
    final ZeebeDb<ColumnFamilies> zeebeDb = dbFactory.createDb();
    transactionContext = zeebeDb.createContext();

    oneKey = new DbLong();
    oneValue = new DbLong();
    oneColumnFamily =
        zeebeDb.createColumnFamily(ColumnFamilies.ONE, transactionContext, oneKey, oneValue);

    twoKey = new DbLong();
    twoValue = new DbLong();
    twoColumnFamily =
        zeebeDb.createColumnFamily(ColumnFamilies.TWO, transactionContext, twoKey, twoValue);

    threeKey = new DbLong();
    threeValue = new DbLong();
    threeColumnFamily =
        zeebeDb.createColumnFamily(ColumnFamilies.THREE, transactionContext, threeKey, threeValue);
  }

  @Test
  void shouldUseTransaction() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    // when
    transactionContext.runInTransaction(
        () -> {
          oneColumnFamily.upsert(oneKey, oneValue);
          twoColumnFamily.upsert(twoKey, twoValue);
          threeColumnFamily.upsert(threeKey, threeValue);
        });

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isTrue();
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    assertThat(threeColumnFamily.exists(threeKey)).isTrue();
  }

  @Test
  void shouldNotGetPreviousValue() {
    // given
    oneKey.wrapLong(123);
    oneValue.wrapLong(456);

    transactionContext.runInTransaction(
        () -> {
          oneColumnFamily.upsert(oneKey, oneValue);
          oneColumnFamily.get(oneKey);
          oneKey.wrapLong(-1);

          // when
          final DbLong zbLong = oneColumnFamily.get(oneKey);

          // then
          assertThat(zbLong).isNull();
        });
  }

  @Test
  void shouldStartNewTransaction() throws Exception {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    final ZeebeDbTransaction transaction = transactionContext.getCurrentTransaction();
    transaction.run(
        () -> {
          oneColumnFamily.upsert(oneKey, oneValue);
          twoColumnFamily.upsert(twoKey, twoValue);
          threeColumnFamily.upsert(threeKey, threeValue);
        });

    // when
    transaction.commit();

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isTrue();
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    assertThat(threeColumnFamily.exists(threeKey)).isTrue();
    assertThat(((InMemoryDbTransaction) transaction).isInCurrentTransaction()).isFalse();
  }

  @Test
  void shouldAccessOnOpenTransaction() throws Exception {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    final ZeebeDbTransaction transaction = transactionContext.getCurrentTransaction();
    transaction.run(
        () -> {
          oneColumnFamily.upsert(oneKey, oneValue);
          twoColumnFamily.upsert(twoKey, twoValue);
          threeColumnFamily.upsert(threeKey, threeValue);
        });

    // when
    // no commit

    // then
    // uses the same transaction
    assertThat(oneColumnFamily.exists(oneKey)).isTrue();
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    assertThat(threeColumnFamily.exists(threeKey)).isTrue();
  }

  @Test
  void shouldNotReopenTransaction() throws Exception {
    // given
    final ZeebeDbTransaction transaction = transactionContext.getCurrentTransaction();

    transaction.run(
        () -> {

          // when
          final ZeebeDbTransaction sameTransaction = transactionContext.getCurrentTransaction();

          // then
          assertThat(transaction).isEqualTo(sameTransaction);
        });
  }

  @Test
  void shouldNotReopenTransactionWithOperations() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    transactionContext.runInTransaction(
        () -> {

          // when
          final ZeebeDbTransaction sameTransaction = transactionContext.getCurrentTransaction();
          sameTransaction.run(
              () -> {
                oneColumnFamily.upsert(oneKey, oneValue);
                twoColumnFamily.upsert(twoKey, twoValue);
                threeColumnFamily.upsert(threeKey, threeValue);
              });

          assertThat(oneColumnFamily.exists(oneKey)).isTrue();
          oneColumnFamily.deleteExisting(oneKey);

          assertThat(twoColumnFamily.exists(twoKey)).isTrue();
          assertThat(threeColumnFamily.exists(threeKey)).isTrue();
        });

    // then it is committed but available in this transaction
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    assertThat(threeColumnFamily.exists(threeKey)).isTrue();
  }

  @Test
  void shouldRollbackTransaction() throws Exception {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    final ZeebeDbTransaction transaction = transactionContext.getCurrentTransaction();
    transaction.run(
        () -> {
          oneColumnFamily.upsert(oneKey, oneValue);
          twoColumnFamily.upsert(twoKey, twoValue);
          threeColumnFamily.upsert(threeKey, threeValue);
        });

    // when
    transaction.rollback();

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
    assertThat(twoColumnFamily.exists(twoKey)).isFalse();
    assertThat(threeColumnFamily.exists(threeKey)).isFalse();
  }

  @Test
  void shouldGetValueInTransaction() {
    // given
    final AtomicLong actualValue = new AtomicLong(0);
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    // when
    transactionContext.runInTransaction(
        () -> {
          oneColumnFamily.upsert(oneKey, oneValue);
          final DbLong value = oneColumnFamily.get(oneKey);
          actualValue.set(value.getValue());
        });

    // then
    assertThat(actualValue.get()).isEqualTo(-1);
  }

  @Test
  void shouldFindValueInTransaction() {
    // given
    final Map<Long, Long> actualValues = new HashMap<>();
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);
    oneColumnFamily.upsert(oneKey, oneValue);

    // when
    transactionContext.runInTransaction(
        () -> {
          // update value
          oneKey.wrapLong(1);
          oneValue.wrapLong(-2);
          oneColumnFamily.upsert(oneKey, oneValue);

          // create new key-value pair
          oneKey.wrapLong(2);
          oneValue.wrapLong(-3);
          oneColumnFamily.upsert(oneKey, oneValue);

          actualValues.put(oneKey.getValue(), oneColumnFamily.get(oneKey).getValue());
          oneKey.wrapLong(1);
          actualValues.put(oneKey.getValue(), oneColumnFamily.get(oneKey).getValue());
        });

    // then
    final Map<Long, Long> expectedValues = new HashMap<>();
    expectedValues.put(1L, -2L);
    expectedValues.put(2L, -3L);
    assertThat(actualValues).isEqualTo(expectedValues);
  }

  @Test
  void shouldIterateAndFindValuesInTransaction() {
    // given
    final Map<Long, Long> actualValues = new HashMap<>();

    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);
    oneColumnFamily.upsert(oneKey, oneValue);

    oneKey.wrapLong(2);
    oneValue.wrapLong(-2);
    oneColumnFamily.upsert(oneKey, oneValue);

    // when
    transactionContext.runInTransaction(
        () -> {
          // update old value
          oneKey.wrapLong(2);
          oneValue.wrapLong(-5);
          oneColumnFamily.upsert(oneKey, oneValue);

          // create new key-value pair
          oneKey.wrapLong(3);
          oneValue.wrapLong(-3);
          oneColumnFamily.upsert(oneKey, oneValue);

          oneColumnFamily.forEach((k, v) -> actualValues.put(k.getValue(), v.getValue()));
        });

    // then
    final Map<Long, Long> expectedValues = new HashMap<>();
    expectedValues.put(1L, -1L);
    expectedValues.put(2L, -5L);
    expectedValues.put(3L, -3L);
    assertThat(actualValues).isEqualTo(expectedValues);
  }

  @Test
  void shouldIterateAndDeleteInTransaction() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);
    oneColumnFamily.upsert(oneKey, oneValue);

    oneKey.wrapLong(2);
    oneValue.wrapLong(-2);
    oneColumnFamily.upsert(oneKey, oneValue);

    // when
    transactionContext.runInTransaction(
        () -> oneColumnFamily.forEach((k, v) -> oneColumnFamily.deleteExisting(k)));

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
    oneKey.wrapLong(2);
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
  }

  @Test
  void shouldEndInSameTransaction() {
    // given
    final AtomicLong actualValue = new AtomicLong(0);
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);
    oneColumnFamily.upsert(oneKey, oneValue);

    twoValue.wrapLong(192313);

    // when
    oneColumnFamily.upsert(oneKey, oneValue);
    transactionContext.runInTransaction(
        () -> {
          transactionContext.runInTransaction(() -> oneColumnFamily.upsert(oneKey, twoValue));
          final DbLong value = oneColumnFamily.get(oneKey);
          actualValue.set(value.getValue());
        });

    // then
    assertThat(actualValue.get()).isEqualTo(192313);
    assertThat(oneColumnFamily.get(oneKey).getValue()).isEqualTo(192313);
  }

  @Test
  void shouldWriteAndDeleteInTransaction() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);
    twoColumnFamily.upsert(twoKey, twoValue);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);
    threeColumnFamily.upsert(threeKey, threeValue);

    // when
    transactionContext.runInTransaction(
        () -> {
          // create
          oneColumnFamily.upsert(oneKey, oneValue);

          // delete
          twoColumnFamily.deleteExisting(twoKey);

          // update
          threeValue.wrapLong(Integer.MIN_VALUE);
          threeColumnFamily.upsert(threeKey, threeValue);
        });

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isTrue();
    assertThat(oneColumnFamily.get(oneKey).getValue()).isEqualTo(-1);

    assertThat(twoColumnFamily.exists(twoKey)).isFalse();

    assertThat(threeColumnFamily.exists(threeKey)).isTrue();
    assertThat(threeColumnFamily.get(threeKey).getValue()).isEqualTo(Integer.MIN_VALUE);
  }

  @Test
  void shouldWriteAndDeleteSameKeyValuePairInTransaction() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    // when
    transactionContext.runInTransaction(
        () -> {
          // create
          oneColumnFamily.upsert(oneKey, oneValue);

          // delete
          oneColumnFamily.deleteExisting(oneKey);
        });

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
  }

  @Test
  void shouldAllowDeleteAndInsertInTransaction() throws Exception {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1L);
    twoValue.wrapLong(-2L);
    oneColumnFamily.insert(oneKey, oneValue);
    transactionContext.getCurrentTransaction().commit();

    // when
    transactionContext.runInTransaction(
        () -> {
          oneColumnFamily.deleteExisting(oneKey);
          oneColumnFamily.insert(oneKey, twoValue);
        });

    // then
    assertThat(oneColumnFamily.get(oneKey).getValue()).isEqualTo(twoValue.getValue());
  }

  @Test
  void shouldNotGetByKeyIfDeletedInTransaction() throws Exception {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1L);
    twoValue.wrapLong(-2L);
    oneColumnFamily.insert(oneKey, oneValue);
    transactionContext.getCurrentTransaction().commit();

    // when
    transactionContext.runInTransaction(
        () -> {
          oneColumnFamily.deleteExisting(oneKey);

          if (oneColumnFamily.get(oneKey) != null) {
            fail("Should not be able to get deleted key.");
          }
        });

    // then
    assertThat(oneColumnFamily.get(oneKey)).isNull();
  }

  @Test
  void shouldNotCommitOnError() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);

    twoKey.wrapLong(52000);
    twoValue.wrapLong(192313);
    twoColumnFamily.upsert(twoKey, twoValue);

    threeKey.wrapLong(Short.MAX_VALUE);
    threeValue.wrapLong(Integer.MAX_VALUE);

    // when
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    try {
      transactionContext.runInTransaction(
          () -> {
            oneColumnFamily.upsert(oneKey, oneValue);
            twoColumnFamily.deleteExisting(twoKey);
            threeColumnFamily.upsert(threeKey, threeValue);
            throw new RuntimeException();
          });
    } catch (final Exception e) {
      // ignore
    }

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
    assertThat(twoColumnFamily.exists(twoKey)).isTrue();
    assertThat(threeColumnFamily.exists(threeKey)).isFalse();
  }

  @Test
  void shouldWriteKeyAfterDeletion() {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1);
    oneColumnFamily.upsert(oneKey, oneValue);

    // when
    assertThat(oneColumnFamily.get(oneKey).getValue()).isEqualTo(-1);
    transactionContext.runInTransaction(
        () -> {
          oneColumnFamily.deleteExisting(oneKey);
          oneValue.wrapLong(-2);
          oneColumnFamily.upsert(oneKey, oneValue);
        });

    // then
    assertThat(oneColumnFamily.exists(oneKey)).isTrue();
    assertThat(oneColumnFamily.get(oneKey).getValue()).isEqualTo(-2);
  }

  @Test
  void shouldNotIterateOverDeletionsInTransaction() throws Exception {
    // given
    oneKey.wrapLong(1);
    oneValue.wrapLong(-1L);
    oneColumnFamily.insert(oneKey, oneValue);
    transactionContext.getCurrentTransaction().commit();

    // when - then
    transactionContext.runInTransaction(
        () -> {
          oneColumnFamily.deleteExisting(oneKey);
          oneColumnFamily.forEach(
              (key, value) -> {
                fail("Should not iterate over deleted keys");
              });
        });
  }

  private enum ColumnFamilies implements EnumValue {
    DEFAULT(0), // rocksDB needs a default column family
    ONE(1),
    TWO(2),
    THREE(3);

    private final int value;

    ColumnFamilies(final int value) {
      this.value = value;
    }

    @Override
    public int getValue() {
      return value;
    }
  }
}
