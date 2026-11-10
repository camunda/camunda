/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.jdbc.MixedTransactionContext;
import io.camunda.zeebe.engine.state.instance.RdbmsElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/** Temporary: element instances live in the relational database, everything else in RocksDB. */
public final class MixedStateStores extends RocksDbStateStores {

  private final MixedTransactionContext mixedContext;
  private final int partitionId;

  public MixedStateStores(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final MixedTransactionContext mixedContext) {
    super(zeebeDb, mixedContext.rocksDb());
    this.partitionId = partitionId;
    this.mixedContext = mixedContext;
  }

  @Override
  public MutableElementInstanceState elementInstanceState(
      final MutableVariableState variableState) {
    return new RdbmsElementInstanceState(mixedContext.jdbc(), partitionId, variableState);
  }
}
