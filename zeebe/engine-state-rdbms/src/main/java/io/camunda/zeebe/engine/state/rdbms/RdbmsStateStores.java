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
import io.camunda.zeebe.engine.state.instance.EventScopeInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.JobStateImpl;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsStateImpl;
import io.camunda.zeebe.engine.state.metrics.UsageMetricStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.variable.VariableStateImpl;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.time.InstantSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jspecify.annotations.Nullable;

/**
 * Element instances, variables, jobs, event scopes and process definitions live in the relational
 * database, everything else stays in RocksDB. Requires the {@link MixedTransactionContext} of a
 * {@code MixedZeebeDb}.
 *
 * <p>The database keeps its rows across restarts, while RocksDB is rebuilt by replaying the log. So
 * each relational state is wrapped to leave its rows alone while the replay applies events whose
 * changes the database already contains, see {@code ReplaySkippingElementInstanceState}.
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
    this(partitionId, zeebeDb, mixedContext, sessionFactory, null);
  }

  private RdbmsStateStores(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final MixedTransactionContext mixedContext,
      final SqlSessionFactory sessionFactory,
      final @Nullable PartitionWriteBehind writeBehind) {
    super(zeebeDb, mixedContext.rocksDb());
    this.zeebeDb = zeebeDb;
    this.mixedContext = mixedContext;
    entitySession =
        writeBehind == null
            ? new EntitySession(mixedContext.jdbc(), partitionId, sessionFactory)
            : new EntitySession(
                mixedContext.jdbc(),
                partitionId,
                sessionFactory,
                writeBehind.store(),
                writeBehind.groups());
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
            resources.sessionFactory(),
            resources.writeBehind(partitionId));
  }

  @Override
  public MutableVariableState variableState() {
    return new ReplaySkippingVariableState(
        new VariableStateImpl(new RdbmsVariableStore(entitySession)), mixedContext.jdbc());
  }

  /**
   * The variable state is this factory's relational one, so the scopes of the element instances are
   * written in the same JDBC transaction as the element instances.
   */
  @Override
  public MutableElementInstanceState elementInstanceState(
      final MutableVariableState variableState) {
    return new ReplaySkippingElementInstanceState(
        new ElementInstanceStateImpl(new RdbmsElementInstanceStore(entitySession), variableState),
        mixedContext.jdbc());
  }

  @Override
  public MutableJobState jobState() {
    return new ReplaySkippingJobState(
        new JobStateImpl(new RdbmsJobStore(entitySession)), mixedContext.jdbc());
  }

  @Override
  public MutableEventScopeInstanceState eventScopeInstanceState() {
    return new ReplaySkippingEventScopeInstanceState(
        new EventScopeInstanceStateImpl(new RdbmsEventScopeStore(entitySession)),
        mixedContext.jdbc());
  }

  @Override
  public MutableUsageMetricState usageMetricState() {
    return new ReplaySkippingUsageMetricState(
        new UsageMetricStateImpl(new RdbmsUsageMetricStore(entitySession)), mixedContext.jdbc());
  }

  @Override
  public MutableJobMetricsState jobMetricsState(
      final InstantSource clock, final EngineConfiguration config) {
    return new ReplaySkippingJobMetricsState(
        new JobMetricsStateImpl(new RdbmsJobMetricsStore(entitySession), clock, config),
        mixedContext.jdbc());
  }

  @Override
  public MutableProcessState processState(
      final EngineConfiguration config, final BpmnTransformer transformer) {
    return new ReplaySkippingProcessState(
        new ProcessStateImpl(
            new RdbmsProcessStore(entitySession, mixedContext.jdbc()),
            config,
            transformer,
            mixedContext.jdbc(),
            zeebeDb.getMeterRegistry()),
        mixedContext.jdbc());
  }
}
