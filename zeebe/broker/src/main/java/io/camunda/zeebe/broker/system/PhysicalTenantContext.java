/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.startup.RdbmsStateDatabase;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.FeatureFlags;
import org.jspecify.annotations.Nullable;

/**
 * Bundles all per-physical-tenant objects required to bootstrap the services for a given physical
 * tenant.
 *
 * <p>BrokerCfg present here contains the full effective configuration to be applied to the physical
 * tenant.
 *
 * <p>exporterRepository contains all exporters that are configured for the physical tenant with the
 * corresponding configuration.
 *
 * <p>secretStoreRegistry holds the tenant's configured secret stores and their caches, read on job
 * activation to inject resolved secrets.
 *
 * <p>rdbmsStateDatabase is where the tenant's partitions keep their state when its primary storage
 * is {@code rdbms}, and null otherwise.
 */
public record PhysicalTenantContext(
    EngineSecurityConfig securityConfig,
    BrokerRequestAuthorizationConverter authorizationConverter,
    FeatureFlags featureFlags,
    BrokerCfg config,
    ExporterRepository exporterRepository,
    SecretStoreRegistry secretStoreRegistry,
    @Nullable RdbmsStateDatabase rdbmsStateDatabase) {

  /** A context for a tenant that keeps its partitions' state in RocksDB. */
  public PhysicalTenantContext(
      final EngineSecurityConfig securityConfig,
      final BrokerRequestAuthorizationConverter authorizationConverter,
      final FeatureFlags featureFlags,
      final BrokerCfg config,
      final ExporterRepository exporterRepository,
      final SecretStoreRegistry secretStoreRegistry) {
    this(
        securityConfig,
        authorizationConverter,
        featureFlags,
        config,
        exporterRepository,
        secretStoreRegistry,
        null);
  }
}
