/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.security.validation.GroupValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.RoleValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.api.EngineDependencies;
import io.camunda.zeebe.engine.api.EngineFactory;
import io.camunda.zeebe.engine.api.MigrationStatusReader;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.identity.initialize.AuthorizationConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.EntityInitializationConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.GroupConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.RoleConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.TenantConfigurer;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.engine.state.message.TransientPendingMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.engine.state.migration.DbMigratorImpl;
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.engine.state.query.StateQueryService;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.ClusterContext;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The engine's implementation of the {@link EngineFactory} the broker is given. */
public final class DefaultEngineFactory implements EngineFactory {

  @Override
  public EngineProcessor createEngine(
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
              dependencies.probeNotifier(),
              dependencies.searchClientsProxy(),
              dependencies.authorizationConverter(),
              dependencies.secretStoreRegistry());
        },
        config,
        securityConfig);
  }

  @Override
  public boolean runMigrations(
      final int partitionId,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final EngineConfiguration config,
      final boolean versionCheckRestrictionEnabled,
      final ClusterContext clusterContext,
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
            versionCheckRestrictionEnabled, clusterContext, processingState, brokerVersion);

    final var migrationsPerformed = migrator.runMigrations();
    zeebeDbContext.getCurrentTransaction().commit();
    return migrationsPerformed.versionChanged();
  }

  @Override
  public QueryService createQueryService(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final InstantSource clock) {
    return new StateQueryService(zeebeDb, clock);
  }

  @Override
  public MigrationStatusReader createMigrationStatusReader(
      final ZeebeDb<ZbColumnFamilies> zeebeDb) {
    final var state = new DbMigrationState(zeebeDb, zeebeDb.createContext());
    return state::getMigratedByVersion;
  }

  @Override
  public void banProcessInstance(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final long processInstanceKey) {
    new DbBannedInstanceState(zeebeDb, zeebeDb.createContext())
        .banProcessInstance(processInstanceKey);
  }

  @Override
  public Map<String, List<String>> validateIdentityInitialization(
      final EngineSecurityConfig securityConfig) {
    final var identifierValidator =
        new IdentifierValidator(
            securityConfig.getIdValidationPattern(), securityConfig.getGroupIdValidationPattern());
    final var initialization = securityConfig.getInitialization();
    final var violations = new LinkedHashMap<String, List<String>>();
    collectViolations(
        violations,
        "authorizations",
        new AuthorizationConfigurer(new AuthorizationValidator(identifierValidator)),
        initialization.getAuthorizations());
    collectViolations(
        violations,
        "tenants",
        new TenantConfigurer(new TenantValidator(identifierValidator)),
        initialization.getTenants());
    collectViolations(
        violations,
        "roles",
        new RoleConfigurer(new RoleValidator(identifierValidator)),
        initialization.getRoles());
    collectViolations(
        violations,
        "groups",
        new GroupConfigurer(new GroupValidator(identifierValidator)),
        initialization.getGroups());
    return violations;
  }

  private static <C, R extends UnifiedRecordValue> void collectViolations(
      final Map<String, List<String>> violations,
      final String entityType,
      final EntityInitializationConfigurer<C, R> configurer,
      final List<C> configured) {
    configurer.configureEntities(configured).ifLeft(found -> violations.put(entityType, found));
  }
}
