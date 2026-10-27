/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.engine.Engine;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.EngineProcessor;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.message.TransientPendingMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.migration.DbMigratorImpl;
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.engine.state.query.StateQueryService;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import java.time.InstantSource;

/**
 * Entry point for the broker to create the engine and its state accessors. This is the only place
 * that instantiates engine implementation classes on behalf of the broker.
 */
public final class EngineFactory {

  private EngineFactory() {}

  public static EngineProcessor createEngine(
      final EngineDependencies dependencies,
      final EngineConfiguration config,
      final EngineSecurityConfig securityConfig) {
    return new Engine(
        context -> {
          final var commandSender = context.getPartitionCommandSender();
          return EngineProcessors.createEngineProcessors(
              context,
              dependencies.partitionCount(),
              new SubscriptionCommandSender(context.getPartitionId(), commandSender),
              commandSender,
              dependencies.featureFlags(),
              dependencies.jobStreamer(),
              dependencies.searchClientsProxy(),
              dependencies.authorizationConverter(),
              dependencies.secretStoreRegistry());
        },
        config,
        securityConfig);
  }

  /**
   * Runs the state migrations of the partition and commits them.
   *
   * @return whether the migrations changed the version recorded in the state
   */
  public static boolean runMigrations(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final EngineConfiguration config,
      final boolean versionCheckRestrictionEnabled,
      final int partitionCount,
      final String brokerVersion)
      throws Exception {
    final var zeebeDbContext = zeebeDb.createContext();
    final var processingState =
        new ProcessingDbState(
            partitionId,
            zeebeDb,
            zeebeDbContext,
            KeyGenerator.immutable(partitionId),
            new TransientPendingSubscriptionState(),
            new TransientPendingSubscriptionState(),
            new TransientPendingMessageStartProcessInstanceAskState(),
            config,
            InstantSource.system(),
            ExpressionLanguageMetrics.noop());
    final var migrator =
        new DbMigratorImpl(
            versionCheckRestrictionEnabled,
            new ClusterContextImpl(partitionCount),
            processingState,
            brokerVersion);

    final var migrationsPerformed = migrator.runMigrations();
    zeebeDbContext.getCurrentTransaction().commit();
    return migrationsPerformed.versionChanged();
  }

  public static QueryService createQueryService(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final InstantSource clock) {
    return new StateQueryService(zeebeDb, clock);
  }

  public static MigrationStatusReader createMigrationStatusReader(final ZeebeDb<ZbColumnFamilies> zeebeDb) {
    return new MigrationStatusReader(zeebeDb);
  }

  /** Bans the process instance in the state of the given, already open, database. */
  public static void banProcessInstance(final ZeebeDb<ZbColumnFamilies> zeebeDb, final long processInstanceKey) {
    new DbBannedInstanceState(zeebeDb, zeebeDb.createContext())
        .banProcessInstance(processInstanceKey);
  }
}
