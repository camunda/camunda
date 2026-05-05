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
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import java.io.File;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the transaction isolation contract for ZeebeTransactionDb backed by WriteBatchWithIndex.
 * These tests own the behavioral guarantee that was previously provided implicitly by RocksDB's
 * OptimisticTransactionDB.
 */
final class ZeebeTransactionIsolationTest {

  @TempDir File tempDir;

  private ZeebeDb<DefaultColumnFamily> db;
  private TransactionContext writeCtx;
  private TransactionContext readCtx;
  private ColumnFamily<DbString, DbString> writeCf;
  private ColumnFamily<DbString, DbString> readCf;
  private final DbString key = new DbString();
  private final DbString value = new DbString();

  @BeforeEach
  void setUp() {
    final ZeebeDbFactory<DefaultColumnFamily> factory = DefaultZeebeDbFactory.getDefaultFactory();
    db = factory.createDb(tempDir);
    writeCtx = db.createContext();
    readCtx = db.createContext();
    writeCf =
        db.createColumnFamily(
            DefaultColumnFamily.DEFAULT, writeCtx, new DbString(), new DbString());
    readCf =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, readCtx, new DbString(), new DbString());
    key.wrapString("key");
    value.wrapString("value");
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }

  @Test
  void shouldNotSeeUncommittedWriteOutsideTransaction() {
    // given - write inside a transaction without committing
    writeCtx.runInTransaction(
        () -> {
          writeCf.upsert(key, value);
          // when - read from a separate context (reads only committed data)
          readCtx.runInTransaction(
              () -> {
                // then - the uncommitted write must not be visible
                assertThat(readCf.get(key)).isNull();
              });
        });
  }

  @Test
  void shouldNotSeeUncommittedDeleteOutsideTransaction() {
    // given - write inside a transaction without committing
    writeCtx.runInTransaction(() -> writeCf.upsert(key, value));

    // when / delete - read from a separate context (reads only committed data)
    writeCtx.runInTransaction(
        () -> {
          writeCf.deleteExisting(key);

          // then - the uncommitted delete must not be visible
          readCtx.runInTransaction(() -> assertThat(readCf.get(key)).isNotNull());
        });
  }

  @Test
  void shouldReadOwnWritesWithinTransaction() {
    // given + when - write and read inside the same transaction
    writeCtx.runInTransaction(
        () -> {
          writeCf.upsert(key, value);
          // then - the write is visible within the same transaction
          final DbString result = writeCf.get(key);
          assertThat(result).hasToString("value");
        });
  }

  @Test
  void shouldSeeWriteAfterCommit() {
    // given - write and commit
    writeCtx.runInTransaction(() -> writeCf.upsert(key, value));

    // when + then - read from an independent context sees the committed value
    readCtx.runInTransaction(() -> assertThat(readCf.get(key)).isNotNull());
  }

  @Test
  void shouldNotSeeWriteAfterRollback() {
    // given - force a rollback by throwing inside runInTransaction
    try {
      writeCtx.runInTransaction(
          () -> {
            writeCf.upsert(key, value);
            throw new RuntimeException("force rollback");
          });
    } catch (final Exception ignored) {
      // expected
    }

    // when + then - the rolled-back write must not be visible
    readCtx.runInTransaction(() -> assertThat(readCf.get(key)).isNull());
  }

  @Test
  void shouldIterateOwnUncommittedWrites() {
    // given + when - write and iterate inside the same transaction
    writeCtx.runInTransaction(
        () -> {
          writeCf.upsert(key, value);

          // then - iteration sees the uncommitted write
          final var found = new MutableBoolean(false);
          writeCf.forEach((k, v) -> found.set(found.get() || k.toString().equals("key")));
          assertThat(found.get()).isTrue();
        });
  }

  @Test
  void shouldNotIterateUncommittedWritesOutsideTransaction() {
    // given - write inside a transaction without committing
    writeCtx.runInTransaction(
        () -> {
          writeCf.upsert(key, value);
          // when - iterate from a separate context
          readCtx.runInTransaction(
              () -> {
                final var found = new MutableBoolean(false);
                readCf.forEach((k, v) -> found.set(found.get() || k.toString().equals("key")));
                assertThat(found.get()).isFalse();
              });
        });
  }

  @Test
  void shouldSeeIteratedWriteAfterCommit() {
    // given - write and commit
    writeCtx.runInTransaction(() -> writeCf.upsert(key, value));

    // when + then - iteration from a separate context sees the committed entry
    readCtx.runInTransaction(
        () -> {
          final var found = new MutableBoolean(false);
          readCf.forEach((k, v) -> found.set(found.get() || k.toString().equals("key")));
          assertThat(found.get()).isTrue();
        });
  }

  @Test
  void shouldNotSeeDeletedKeyWithinTransaction() {
    // given - write and commit a key, then delete it inside a new transaction
    writeCtx.runInTransaction(() -> writeCf.upsert(key, value));

    // when - delete inside a transaction and read in the same transaction
    writeCtx.runInTransaction(
        () -> {
          writeCf.deleteExisting(key);
          // then - the deleted key must not be visible within the transaction
          assertThat(writeCf.get(key)).isNull();
        });
  }

  @Test
  void shouldNotPersistDeleteAfterRollback() {
    // given - write, commit, then delete with a forced rollback
    writeCtx.runInTransaction(() -> writeCf.upsert(key, value));
    try {
      writeCtx.runInTransaction(
          () -> {
            writeCf.deleteExisting(key);
            throw new RuntimeException("force rollback");
          });
    } catch (final Exception ignored) {
      // expected
    }

    // when + then - the original value must still be present after the rolled-back delete
    readCtx.runInTransaction(() -> assertThat(readCf.get(key)).isNotNull());
  }

  @Test
  void shouldHandleMultipleWritesAndCommit() {
    // given - multiple writes in one transaction
    final DbString key2 = new DbString();
    key2.wrapString("key2");
    final DbString value2 = new DbString();
    value2.wrapString("value2");

    // when
    writeCtx.runInTransaction(
        () -> {
          writeCf.upsert(key, value);
          writeCf.upsert(key2, value2);
        });

    // then - all committed writes are visible
    readCtx.runInTransaction(
        () -> {
          assertThat(readCf.get(key)).isNotNull();
          assertThat(readCf.get(key2)).isNotNull();
        });
  }

  @Test
  void shouldHandleMultipleWritesAndRollback() {
    // given - multiple writes with a forced rollback
    final DbString key2 = new DbString();
    key2.wrapString("key2");
    final DbString value2 = new DbString();
    value2.wrapString("value2");

    try {
      writeCtx.runInTransaction(
          () -> {
            writeCf.upsert(key, value);
            writeCf.upsert(key2, value2);
            throw new RuntimeException("force rollback");
          });
    } catch (final Exception ignored) {
      // expected
    }

    // then - none of the rolled-back writes must be visible
    readCtx.runInTransaction(
        () -> {
          assertThat(readCf.get(key)).isNull();
          assertThat(readCf.get(key2)).isNull();
        });
  }

  /**
   * We do not provide snapshot isolation between transactions, meaning a transaction can always
   * read writes (and deletes) that were committed AFTER it was created.
   *
   * <p>This is encoded here as tests as a contract, in case we ever rely on this behavior. If you
   * change it, then you need to also ensure callers/consumers of the transaction API are not
   * affected.
   */
  @Nested
  final class NoSnapshotIsolationTest {

    @Test
    void shouldSeeLaterDeletedKeyFromOtherPriorTransaction() throws Exception {
      // given - write and commit a key, then delete it inside a new transaction
      writeCtx.runInTransaction(() -> writeCf.upsert(key, value));
      final var readTxn = readCtx.getCurrentTransaction();

      // when - delete inside a transaction and read from a previously opened transaction
      writeCtx.runInTransaction(() -> writeCf.deleteExisting(key));

      // then
      final var val = new MutableReference<DbString>();
      readTxn.run(() -> val.set(readCf.get(key)));
      assertThat(val.get()).isNull();
    }

    @Test
    void shouldSeeLaterWriteFromWithinOtherPriorTransaction() throws Exception {
      // given - a read transaction opened before any writes have been committed
      final var readTxn = readCtx.getCurrentTransaction();

      // when - a separate transaction writes and commits a value
      writeCtx.runInTransaction(() -> writeCf.upsert(key, value));

      // then - the previously opened read transaction sees the later commit
      final var val = new MutableReference<DbString>();
      readTxn.run(() -> val.set(readCf.get(key)));
      assertThat(val.get()).isNotNull();
    }
  }
}
