/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.util.exception.RecoverableException;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import org.rocksdb.Status.Code;
import org.rocksdb.Status.SubCode;

final class ZeebeRocksDbTransactionTest {

  @TempDir File tempDir;
  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  private TransactionContext transactionContext;

  @BeforeEach
  void setup() throws Exception {
    final ZeebeDb<DefaultColumnFamily> zeebeDb = dbFactory.createDb(tempDir);
    transactionContext = zeebeDb.createContext();
  }

  @Test
  void shouldThrowRecoverableException() {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when / then
    assertThatThrownBy(
            () ->
                transactionContext.runInTransaction(
                    () -> {
                      throw new RocksDBException("expected", status);
                    }))
        .isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldReThrowRecoverableException() {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when / then
    assertThatThrownBy(
            () ->
                transactionContext.runInTransaction(
                    () -> {
                      throw new RecoverableException(new RocksDBException("expected", status));
                    }))
        .isInstanceOf(RecoverableException.class);
  }

  @Test
  void shouldWrapExceptionInRuntimeException() {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");

    // when / then
    assertThatThrownBy(
            () ->
                transactionContext.runInTransaction(
                    () -> {
                      throw new RocksDBException("expected", status);
                    }))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldThrowRecoverableExceptionOnCommit() throws Exception {
    // given
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final TransactionContext newContext = new DefaultTransactionContext(transaction);
    final Status status = new Status(Code.IOError, SubCode.None, "");
    doThrow(new RocksDBException("expected", status)).when(transaction).commitInternal();

    // when / then
    assertThatThrownBy(() -> newContext.runInTransaction(() -> {}))
        .isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldWrapExceptionInRuntimeExceptionOnCommit() throws Exception {
    // given
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final TransactionContext newContext = new DefaultTransactionContext(transaction);
    final Status status = new Status(Code.NotSupported, SubCode.None, "");
    doThrow(new RocksDBException("expected", status)).when(transaction).commitInternal();

    // when / then
    assertThatThrownBy(() -> newContext.runInTransaction(() -> {}))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldThrowRecoverableExceptionOnRollback() throws Exception {
    // given - commit fails (triggering rollback), and rollback also fails with a recoverable error
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final TransactionContext newContext = new DefaultTransactionContext(transaction);
    final Status commitStatus = new Status(Code.IOError, SubCode.None, "");
    final Status rollbackStatus = new Status(Code.IOError, SubCode.None, "");
    doThrow(new RocksDBException("commit failed", commitStatus)).when(transaction).commitInternal();
    doThrow(new RocksDBException("rollback failed", rollbackStatus))
        .when(transaction)
        .rollbackInternal();

    // when / then
    assertThatThrownBy(() -> newContext.runInTransaction(() -> {}))
        .isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldWrapExceptionInRuntimeExceptionOnRollback() throws Exception {
    // given - commit fails (triggering rollback), and rollback also fails with a non-recoverable
    // error
    final ZeebeTransaction transaction = mock(ZeebeTransaction.class);
    final TransactionContext newContext = new DefaultTransactionContext(transaction);
    final Status commitStatus = new Status(Code.IOError, SubCode.None, "");
    final Status rollbackStatus = new Status(Code.NotSupported, SubCode.None, "");
    doThrow(new RocksDBException("commit failed", commitStatus)).when(transaction).commitInternal();
    doThrow(new RocksDBException("rollback failed", rollbackStatus))
        .when(transaction)
        .rollbackInternal();

    // when / then
    assertThatThrownBy(() -> newContext.runInTransaction(() -> {}))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldThrowRecoverableExceptionInTransactionRun() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when / then
    final ZeebeDbTransaction currentTransaction = transactionContext.getCurrentTransaction();
    assertThatThrownBy(
            () ->
                currentTransaction.run(
                    () -> {
                      throw new RocksDBException("expected", status);
                    }))
        .isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldReThrowRecoverableExceptionInTransactionRun() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");

    // when / then
    final ZeebeDbTransaction currentTransaction = transactionContext.getCurrentTransaction();
    assertThatThrownBy(
            () ->
                currentTransaction.run(
                    () -> {
                      throw new RecoverableException(new RocksDBException("expected", status));
                    }))
        .isInstanceOf(RecoverableException.class);
  }

  @Test
  void shouldReThrowExceptionFromTransactionRun() throws Exception {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");

    // when / then
    final ZeebeDbTransaction currentTransaction = transactionContext.getCurrentTransaction();
    assertThatThrownBy(
            () ->
                currentTransaction.run(
                    () -> {
                      throw new RocksDBException("expected", status);
                    }))
        .isInstanceOf(RocksDBException.class);
  }

  @Test
  void shouldThrowRecoverableExceptionInTransactionCommit() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) transactionContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).commitInternal();

    // when / then
    assertThatThrownBy(currentTransaction::commit).isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldReThrowExceptionFromTransactionCommit() throws Exception {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) transactionContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).commitInternal();

    // when / then
    assertThatThrownBy(currentTransaction::commit).isInstanceOf(RocksDBException.class);
  }

  @Test
  void shouldThrowRecoverableExceptionInTransactionRollback() throws Exception {
    // given
    final Status status = new Status(Code.IOError, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) transactionContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).rollbackInternal();

    // when / then
    assertThatThrownBy(currentTransaction::rollback).isInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldReThrowExceptionFromTransactionRollback() throws Exception {
    // given
    final Status status = new Status(Code.NotSupported, SubCode.None, "");
    final ZeebeTransaction currentTransaction =
        spy((ZeebeTransaction) transactionContext.getCurrentTransaction());
    doThrow(new RocksDBException("expected", status)).when(currentTransaction).rollbackInternal();

    // when / then
    assertThatThrownBy(currentTransaction::rollback).isInstanceOf(RocksDBException.class);
  }
}
