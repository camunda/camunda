/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * Chooses where the engine state of a partition is stored. The broker hands a factory to the
 * engine, which calls it once the stream processor has provided the database and its transaction
 * context.
 */
@FunctionalInterface
public interface ProcessingStateStoresFactory {

  ProcessingStateStores create(
      int partitionId, ZeebeDb<ZbColumnFamilies> zeebeDb, TransactionContext transactionContext);

  /** Stores all engine state in RocksDB. */
  static ProcessingStateStoresFactory rocksDb() {
    return (partitionId, zeebeDb, transactionContext) ->
        new RocksDbStateStores(zeebeDb, transactionContext);
  }
}
