/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import java.util.Collection;

/**
 * The single reusable transaction of the layered context, mirroring the lifecycle of the
 * RocksDB-backed reference transaction: the staging layer of every layered store plays the role of
 * the write batch. {@link #commit()} promotes staging into the active overlay of every store —
 * purely in memory, no RocksDB write ever happens here — and {@link #rollback()} discards exactly
 * the staged batch, never previously committed state.
 *
 * <p>Owner-thread only, like the stores it drives.
 */
final class LayeredTransaction implements ZeebeDbTransaction {

  private final Collection<LayeredKeyValueStore> stores;
  private boolean inCurrentTransaction;

  /**
   * @param stores a live view over all layered stores of the database; stores created after this
   *     transaction are picked up automatically
   */
  LayeredTransaction(final Collection<LayeredKeyValueStore> stores) {
    this.stores = stores;
  }

  @Override
  public void run(final TransactionOperation operations) throws Exception {
    operations.run();
  }

  @Override
  public void commit() {
    commitInternal();
  }

  @Override
  public void rollback() {
    rollbackInternal();
  }

  void commitInternal() {
    inCurrentTransaction = false;
    stores.forEach(LayeredKeyValueStore::promote);
  }

  void rollbackInternal() {
    inCurrentTransaction = false;
    stores.forEach(LayeredKeyValueStore::discard);
  }

  /** Analog of clearing the reference implementation's write batch on reuse. */
  void resetTransaction() {
    stores.forEach(LayeredKeyValueStore::discard);
    inCurrentTransaction = true;
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }
}
