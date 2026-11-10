/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.jdbc.MixedTransactionContext;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.ProcessingStateStoresFactory;
import io.camunda.zeebe.engine.state.RocksDbStateStores;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * Element instances and process definitions live in the relational database, everything else stays
 * in RocksDB. Requires the {@link MixedTransactionContext} of a {@code MixedZeebeDb}.
 */
public final class RdbmsStateStores extends RocksDbStateStores {

  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final MixedTransactionContext mixedContext;
  private final int partitionId;

  public RdbmsStateStores(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final MixedTransactionContext mixedContext) {
    super(zeebeDb, mixedContext.rocksDb());
    this.zeebeDb = zeebeDb;
    this.partitionId = partitionId;
    this.mixedContext = mixedContext;
  }

  /** Selects these stores for a partition whose database is a {@code MixedZeebeDb}. */
  public static ProcessingStateStoresFactory factory() {
    return (partitionId, zeebeDb, transactionContext) ->
        new RdbmsStateStores(partitionId, zeebeDb, (MixedTransactionContext) transactionContext);
  }

  @Override
  public MutableElementInstanceState elementInstanceState(
      final MutableVariableState variableState) {
    return new ElementInstanceStateImpl(
        new RdbmsElementInstanceStore(mixedContext.jdbc(), partitionId), variableState);
  }

  @Override
  public MutableProcessState processState(
      final EngineConfiguration config, final BpmnTransformer transformer) {
    return new RdbmsProcessState(
        mixedContext.jdbc(), partitionId, config, transformer, zeebeDb.getMeterRegistry());
  }
}
