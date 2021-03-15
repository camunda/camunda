/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl.rocksdb.transaction;

@FunctionalInterface
interface TransactionConsumer {

  /**
   * Consumes a transaction, in order to make sure that a transaction is open.
   *
   * @param transaction the to consumed transaction
   * @throws Exception if an unexpected exception occurs, on opening a new transaction for example
   */
  void run(ZeebeTransaction transaction) throws Exception;
}
