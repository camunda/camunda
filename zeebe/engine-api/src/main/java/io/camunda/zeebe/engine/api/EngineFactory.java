/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.EngineProcessor;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.ClusterContext;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;

/**
 * Everything the broker needs from the engine implementation. The broker only sees this interface;
 * the engine provides the implementation and the application wires it into the broker.
 */
public interface EngineFactory {

  /** Creates the engine of a partition. */
  EngineProcessor createEngine(
      EngineDependencies dependencies,
      EngineConfiguration config,
      EngineSecurityConfig securityConfig);

  /**
   * Runs the state migrations of the partition and commits them.
   *
   * @return whether the migrations changed the version recorded in the state
   */
  boolean runMigrations(
      int partitionId,
      ZeebeDb<ZbColumnFamilies> zeebeDb,
      EngineConfiguration config,
      boolean versionCheckRestrictionEnabled,
      ClusterContext clusterContext,
      String brokerVersion)
      throws Exception;

  /** Creates the service answering queries against the state of the given database. */
  QueryService createQueryService(ZeebeDb<ZbColumnFamilies> zeebeDb, InstantSource clock);

  /** Creates a reader of the migration status in the state of the given database. */
  MigrationStatusReader createMigrationStatusReader(ZeebeDb<ZbColumnFamilies> zeebeDb);

  /** Bans the process instance in the state of the given, already open, database. */
  void banProcessInstance(ZeebeDb<ZbColumnFamilies> zeebeDb, long processInstanceKey);

  /**
   * Validates the identity entities configured for initialization in the given security
   * configuration, without initializing anything.
   *
   * @return the violations per entity type, keyed by the plural entity name ("authorizations",
   *     "tenants", "roles" or "groups"); empty if the configuration is valid
   */
  Map<String, List<String>> validateIdentityInitialization(EngineSecurityConfig securityConfig);
}
