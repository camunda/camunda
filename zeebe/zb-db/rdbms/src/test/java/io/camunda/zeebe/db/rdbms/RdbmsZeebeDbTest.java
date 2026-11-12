/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the relational storage adds to the shared ZeebeDb contract tests: the database's own errors,
 * connections and the rows of more than one partition.
 */
final class RdbmsZeebeDbTest {

  // short, so that a test waiting for a row lock fails fast
  private final TestH2Database database = new TestH2Database(Duration.ofMillis(100));
  private final RdbmsStorage storage = RdbmsStorage.open(database.dataSource(), "");
  private final List<ZeebeDb<?>> dbs = new ArrayList<>();
  @TempDir private Path directory;

  @AfterEach
  void tearDown() throws Exception {
    for (final var db : dbs) {
      db.close();
    }
    database.close();
  }

  @Test
  void shouldReadOwnWritesBeforeCommit() {
    // given
    final var db = createDb(storage, 1);
    final var context = db.createContext();
    final var columnFamily = columnFamily(db, context);
    final var keys = new ArrayList<Long>();

    // when
    context.runInTransaction(
        () -> {
          columnFamily.insert(key(1), value("a"));
          columnFamily.insert(key(2), value("b"));
          columnFamily.deleteExisting(key(2));
          columnFamily.upsert(key(3), value("c"));

          // then
          assertThat(columnFamily.get(key(1))).hasToString("a");
          assertThat(columnFamily.exists(key(2))).isFalse();
          columnFamily.forEachKey(key -> keys.add(key.getValue()));
        });
    assertThat(keys).containsExactly(1L, 3L);
  }

  @Test
  void shouldDiscardWritesOnRollback() throws Exception {
    // given
    final var db = createDb(storage, 1);
    final var context = db.createContext();
    final var columnFamily = columnFamily(db, context);
    final var transaction = context.getCurrentTransaction();
    columnFamily.upsert(key(1), value("sent"));
    columnFamily.count();
    columnFamily.upsert(key(2), value("not sent"));

    // when
    transaction.rollback();

    // then
    assertThat(columnFamily.isEmpty()).isTrue();
  }

  @Test
  void shouldDiscardWritesWhenTheOperationFails() {
    // given
    final var db = createDb(storage, 1);
    final var context = db.createContext();
    final var columnFamily = columnFamily(db, context);
    final var failure = new IllegalStateException("expected");

    // when
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      columnFamily.upsert(key(1), value("sent"));
                      columnFamily.count();
                      columnFamily.upsert(key(2), value("not sent"));
                      throw failure;
                    }))
        .isSameAs(failure);

    // then
    assertThat(columnFamily.isEmpty()).isTrue();
  }

  @Test
  void shouldKeepTheStatesOfPartitionsApart() {
    // given
    final var first = createDb(storage, 1);
    final var firstColumnFamily = columnFamily(first, first.createContext());
    final var second = createDb(storage, 2);
    final var secondColumnFamily = columnFamily(second, second.createContext());

    // when
    firstColumnFamily.insert(key(1), value("first"));
    secondColumnFamily.insert(key(1), value("second"));
    secondColumnFamily.insert(key(2), value("second"));

    // then
    assertThat(firstColumnFamily.get(key(1))).hasToString("first");
    assertThat(firstColumnFamily.count()).isOne();
    assertThat(secondColumnFamily.get(key(1))).hasToString("second");
    assertThat(secondColumnFamily.count()).isEqualTo(2);
  }

  @Test
  void shouldKeepTheColumnFamiliesApart() {
    // given
    final var db = createDb(storage, 1);
    final var context = db.createContext();
    final var first = columnFamily(db, context);
    final var second =
        db.createColumnFamily(TestColumnFamilies.SECOND, context, new DbLong(), new DbString());

    // when
    first.insert(key(1), value("first"));

    // then
    assertThat(second.exists(key(1))).isFalse();
    assertThat(second.isEmpty()).isTrue();
  }

  @Test
  void shouldOrderKeysByTheirUnsignedBytes() {
    // given
    final var db = createDb(storage, 1);
    final var columnFamily = columnFamily(db, db.createContext());
    // big-endian, so -1 is 0xFF.. and comes last, as in RocksDB
    for (final var key : List.of(-1L, Long.MAX_VALUE, 1L)) {
      columnFamily.insert(key(key), value("v"));
    }
    final var forward = new ArrayList<Long>();
    final var reverse = new ArrayList<Long>();

    // when
    columnFamily.forEachKey(key -> forward.add(key.getValue()));
    columnFamily.whileTrueReverse(key(-1), key -> reverse.add(key.getValue()));

    // then
    assertThat(forward).containsExactly(1L, Long.MAX_VALUE, -1L);
    assertThat(reverse).containsExactly(-1L, Long.MAX_VALUE, 1L);
  }

  @Test
  void shouldThrowWhatTheVisitorThrows() {
    // given
    final var db = createDb(storage, 1);
    final var columnFamily = columnFamily(db, db.createContext());
    columnFamily.insert(key(1), value("v"));
    final var failure = new IllegalStateException("expected");

    // when / then
    assertThatThrownBy(
            () ->
                columnFamily.forEach(
                    value -> {
                      throw failure;
                    }))
        .isSameAs(failure);
  }

  @Test
  void shouldReportALockTimeoutAsRecoverable() {
    // given a transaction that sent a write but hasn't committed, so it holds the row's lock
    final var db = createDb(storage, 1);
    final var holder = db.createContext();
    final var holderColumnFamily = columnFamily(db, holder);
    holder.getCurrentTransaction();
    holderColumnFamily.upsert(key(1), value("holder"));
    holderColumnFamily.count();
    final var waiter = db.createContext();
    final var waiterColumnFamily = columnFamily(db, waiter);

    // when / then
    assertThatThrownBy(
            () -> waiter.runInTransaction(() -> waiterColumnFamily.upsert(key(1), value("waiter"))))
        .isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldReportALostConnectionAsRecoverable() {
    // given
    final var connectionLost = new AtomicBoolean();
    final var dataSource = failingDataSource(database.dataSource(), connectionLost);
    final var db = createDb(RdbmsStorage.open(dataSource, ""), 1);
    final var columnFamily = columnFamily(db, db.createContext());
    connectionLost.set(true);

    // when / then
    assertThatThrownBy(() -> columnFamily.get(key(1))).isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldReportOtherDatabaseErrorsAsUnexpected() throws Exception {
    // given
    final var db = createDb(storage, 1);
    final var columnFamily = columnFamily(db, db.createContext());
    database.execute("DROP TABLE " + StateTables.of(TestColumnFamilies.FIRST.getValue()).name());

    // when / then
    assertThatThrownBy(() -> columnFamily.get(key(1)))
        .isNotInstanceOf(ZeebeDbException.class)
        .hasMessage("Unexpected error occurred during RDBMS transaction.");
  }

  @Test
  void shouldReturnTheConnectionsOfOpenTransactionsWhenClosed() throws Exception {
    // given a pool with one connection, which an open transaction holds
    final var pool = JdbcConnectionPool.create(database.url(), "", "");
    pool.setMaxConnections(1);
    pool.setLoginTimeout(1);
    try {
      final var poolStorage = RdbmsStorage.open(pool, "");
      final var db = createDb(poolStorage, 1);
      final var context = db.createContext();
      final var columnFamily = columnFamily(db, context);
      context.getCurrentTransaction();
      columnFamily.upsert(key(1), value("not committed"));
      columnFamily.count();

      // when
      db.close();

      // then
      final var other = createDb(poolStorage, 1);
      assertThat(columnFamily(other, other.createContext()).isEmpty()).isTrue();
    } finally {
      pool.dispose();
    }
  }

  private ZeebeDb<TestColumnFamilies> createDb(final RdbmsStorage storage, final int partitionId) {
    final var db =
        new RdbmsZeebeDbFactory<TestColumnFamilies>(
                storage,
                partitionId,
                () -> 1,
                new ConsistencyChecksSettings(true, true),
                SimpleMeterRegistry::new)
            .createDb(directory.toFile());
    dbs.add(db);
    return db;
  }

  private static ColumnFamily<DbLong, DbString> columnFamily(
      final ZeebeDb<TestColumnFamilies> db, final TransactionContext context) {
    return db.createColumnFamily(TestColumnFamilies.FIRST, context, new DbLong(), new DbString());
  }

  private static DbLong key(final long value) {
    final var key = new DbLong();
    key.wrapLong(value);
    return key;
  }

  private static DbString value(final String value) {
    final var dbString = new DbString();
    dbString.wrapString(value);
    return dbString;
  }

  /** Throws a transient error for every new connection once {@code connectionLost} is set. */
  private static DataSource failingDataSource(
      final DataSource dataSource, final AtomicBoolean connectionLost) {
    return (DataSource)
        Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
              if (connectionLost.get() && method.getName().equals("getConnection")) {
                throw new SQLTransientConnectionException("connection lost");
              }
              try {
                return method.invoke(dataSource, args);
              } catch (final InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }
}
