/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;

/**
 * Experimental: a {@link TransactionContext} that spans a RocksDB context and a JDBC context, so
 * that state stored in either store is committed or rolled back together.
 *
 * <p>Commits the JDBC transaction first, then the RocksDB one. A failure between the two commits
 * leaves the database one command ahead of RocksDB. As RocksDB is rebuilt from a snapshot and the
 * log on restart anyway, the stores converge: RocksDB re-applies the events of that command, while
 * the JDBC side discards them, since the database contains the command's position (see {@link
 * JdbcTransactionContext#markAsProcessed}).
 */
public final class MixedTransactionContext implements TransactionContext {

  private final TransactionContext rocksDb;
  private final JdbcTransactionContext jdbc;

  public MixedTransactionContext(
      final TransactionContext rocksDb, final JdbcTransactionContext jdbc) {
    this.rocksDb = rocksDb;
    this.jdbc = jdbc;
  }

  /** The context RocksDB column families must be created with. */
  public TransactionContext rocksDb() {
    return rocksDb;
  }

  public JdbcTransactionContext jdbc() {
    return jdbc;
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    rocksDb.runInTransaction(() -> jdbc.runInTransaction(operations));
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    return new MixedTransaction(rocksDb.getCurrentTransaction(), jdbc.getCurrentTransaction());
  }

  /**
   * Listens to the RocksDB transaction: it commits after the JDBC one and is always rolled back, so
   * its outcome is the outcome of the mixed transaction.
   */
  @Override
  public void addTransactionListener(final TransactionListener listener) {
    rocksDb.addTransactionListener(listener);
  }

  private record MixedTransaction(
      ZeebeDbTransaction rocksDbTransaction, ZeebeDbTransaction jdbcTransaction)
      implements ZeebeDbTransaction {

    @Override
    public void run(final TransactionOperation operations) throws Exception {
      rocksDbTransaction.run(() -> jdbcTransaction.run(operations));
    }

    @Override
    public void commit() throws Exception {
      jdbcTransaction.commit();
      rocksDbTransaction.commit();
    }

    @Override
    public void rollback() throws Exception {
      try {
        jdbcTransaction.rollback();
      } finally {
        rocksDbTransaction.rollback();
      }
    }
  }
}
