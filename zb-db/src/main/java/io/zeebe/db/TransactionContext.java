/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db;

/** Represents the transaction context to interact with the database in a transaction. */
public interface TransactionContext {

  /**
   * Runs the commands like delete, put etc. in a transaction. Access of different column families
   * inside this transaction are possible.
   *
   * <p>Reading key-value pairs via get or an iterator is also possible and will reflect changes,
   * which are made during the transaction.
   *
   * <p><b>NOTE</b>: It is allowed to nest these calls. The transaction will then be reused (it will
   * run in the same transaction). If the outer call ends, the transaction will be committed. On an
   * error the transaction is rolled back.
   *
   * @param operations the operations
   * @throws ZeebeDbException is thrown on an unexpected error in the database layer
   * @throws RuntimeException is thrown on an unexpected error in executing the operations
   */
  void runInTransaction(TransactionOperation operations);

  /**
   * This will return an transaction object, on which the caller can operate on. The caller is free
   * to decide when to commit or rollback the transaction.
   *
   * @return the transaction object
   */
  ZeebeDbTransaction getCurrentTransaction();
}
