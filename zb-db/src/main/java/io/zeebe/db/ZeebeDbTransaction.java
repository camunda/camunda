/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db;

/** Represents an Zeebe DB transaction, which can be committed or on error it can be rolled back. */
public interface ZeebeDbTransaction {

  /**
   * Runs the commands like delete, put etc. in the current transaction. Access of different column
   * families inside this transaction are possible.
   *
   * <p>Reading key-value pairs via get or an iterator is also possible and will reflect changes,
   * which are made during the transaction.
   *
   * @param operations the operations
   * @throws ZeebeDbException is thrown on an unexpected error in the database layer
   * @throws RuntimeException is thrown on an unexpected error in executing the operations
   */
  void run(TransactionOperation operations) throws Exception;

  /**
   * Commits the transaction and writes the data into the database.
   *
   * @throws ZeebeDbException if the underlying database has a recoverable exception thrown
   * @throws Exception if the underlying database has a non recoverable exception thrown
   */
  void commit() throws Exception;

  /**
   * Rolls the transaction back to the latest commit, discards all changes in between.
   *
   * @throws ZeebeDbException if the underlying database has a recoverable exception thrown
   * @throws Exception if the underlying database has a non recoverable exception thrown
   */
  void rollback() throws Exception;
}
