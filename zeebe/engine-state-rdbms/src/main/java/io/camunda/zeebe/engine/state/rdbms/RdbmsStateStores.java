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
import io.camunda.zeebe.engine.state.deployment.ProcessStateImpl;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Element instances and process definitions live in the relational database, everything else stays
 * in RocksDB. Requires the {@link MixedTransactionContext} of a {@code MixedZeebeDb}.
 */
public final class RdbmsStateStores extends RocksDbStateStores {

  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final MixedTransactionContext mixedContext;
  private final EntitySession entitySession;

  /** Creates the partition's one {@link EntitySession}, shared by all its relational stores. */
  public RdbmsStateStores(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final MixedTransactionContext mixedContext,
      final SqlSessionFactory sessionFactory) {
    super(zeebeDb, mixedContext.rocksDb());
    this.zeebeDb = zeebeDb;
    this.mixedContext = mixedContext;
    entitySession = new EntitySession(mixedContext.jdbc(), partitionId, sessionFactory);
    mixedContext.jdbc().register(entitySession);
  }

  /**
   * Selects these stores for a partition whose database is a {@code MixedZeebeDb}.
   *
   * @param sessionFactory built once for the database, see {@link
   *     EngineStateMyBatis#createSqlSessionFactory}
   */
  public static ProcessingStateStoresFactory factory(final RdbmsEngineStateResources resources) {
    return (partitionId, zeebeDb, transactionContext) ->
        new RdbmsStateStores(
            partitionId,
            zeebeDb,
            (MixedTransactionContext) transactionContext,
            resources.sessionFactory());
  }

  @Override
  public MutableElementInstanceState elementInstanceState(
      final MutableVariableState variableState) {
    return new ElementInstanceStateImpl(
        new RdbmsElementInstanceStore(entitySession), variableState);
  }

  @Override
  public MutableProcessState processState(
      final EngineConfiguration config, final BpmnTransformer transformer) {
    return new ProcessStateImpl(
        new RdbmsProcessStore(entitySession, mixedContext.jdbc()),
        config,
        transformer,
        mixedContext.jdbc(),
        zeebeDb.getMeterRegistry());
  }
}
