/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/**
 * Notified when a transaction of a {@link TransactionContext} ends, so that in-memory state derived
 * from the transaction's reads and writes (e.g. caches) can follow the outcome of the transaction.
 *
 * <p>Listeners are called on the thread that ends the transaction, and must not throw.
 */
public interface TransactionListener {

  /** Called after the changes of the transaction were committed. */
  void onCommit();

  /**
   * Called after the changes of the transaction were discarded. Can also be called for a
   * transaction that made no changes.
   */
  void onRollback();
}
