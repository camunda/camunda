/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.zeebe.util.VisibleForTesting;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Default {@link RdbmsSchemaManagerRegistry} that owns one {@link RdbmsSchemaManager} per physical
 * tenant and initializes each schema at startup.
 *
 * <p>Tenants are initialized sequentially in iteration order and a failure for any single tenant
 * aborts startup — fail-fast preserves the previous single-tenant behaviour. Initializing the
 * tenants in parallel and isolating per-tenant failures is a deferred follow-up
 * (https://github.com/camunda/camunda/issues/54299): the isolated change point is this class's
 * {@link #afterPropertiesSet()} loop, which keeps the per-tenant {@link RdbmsSchemaManager}s
 * unaware of orchestration concerns.
 */
public class DefaultRdbmsSchemaManagerRegistry
    implements RdbmsSchemaManagerRegistry, InitializingBean {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultRdbmsSchemaManagerRegistry.class);

  private final Map<String, RdbmsSchemaManager> schemaManagers;

  @VisibleForTesting
  DefaultRdbmsSchemaManagerRegistry(final Map<String, RdbmsSchemaManager> schemaManagers) {
    this.schemaManagers = schemaManagers;
  }

  /**
   * Builds a registry from the per-tenant {@link PerTenantSchemaConfig} map: each tenant gets a
   * {@link LiquibaseSchemaManager} when {@code auto-ddl} is enabled, or a {@link NoopSchemaManager}
   * when the schema is managed externally.
   */
  public static DefaultRdbmsSchemaManagerRegistry fromConfigs(
      final Map<String, PerTenantSchemaConfig> physicalTenantConfigs,
      final String applicationVersion) {
    final Map<String, RdbmsSchemaManager> schemaManagers = new LinkedHashMap<>();
    physicalTenantConfigs.forEach(
        (physicalTenantId, config) ->
            schemaManagers.put(
                physicalTenantId,
                config.autoDdl()
                    ? new LiquibaseSchemaManager(config, applicationVersion)
                    : new NoopSchemaManager()));
    return new DefaultRdbmsSchemaManagerRegistry(schemaManagers);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    for (final var entry : schemaManagers.entrySet()) {
      final var physicalTenantId = entry.getKey();
      LOG.info("[RDBMS Schema] Initializing schema for physical tenant '{}'.", physicalTenantId);
      entry.getValue().initialize();
      LOG.debug("[RDBMS Schema] Schema initialized for physical tenant '{}'.", physicalTenantId);
    }
  }

  @Override
  public boolean isInitialized(final String physicalTenantId) {
    final var schemaManager = schemaManagers.get(physicalTenantId);
    return schemaManager != null && schemaManager.isInitialized();
  }
}
