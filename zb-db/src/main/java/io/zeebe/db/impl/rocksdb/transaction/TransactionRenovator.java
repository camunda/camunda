/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl.rocksdb.transaction;

import org.rocksdb.Transaction;

@FunctionalInterface
public interface TransactionRenovator {

  /**
   * Renews the given oldTransaction such that it can reused.
   *
   * @param oldTransaction the old transaction which becomes new
   * @return the renewed transaction
   */
  Transaction renewTransaction(Transaction oldTransaction);
}
