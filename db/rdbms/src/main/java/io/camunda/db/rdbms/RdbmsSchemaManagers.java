/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds one {@link RdbmsSchemaManager} per physical tenant from that tenant's {@link
 * PerTenantSchemaConfig}.
 *
 * <p>Which manager a tenant gets is storage knowledge and stays here. Orchestrating the resulting
 * managers — when they run, whether a failure is retried, which tenants count as ready — is not,
 * and belongs to the caller: {@code RdbmsSchemaInitializer} in the distribution, which is where the
 * per-tenant isolation lives.
 */
public final class RdbmsSchemaManagers {

  private RdbmsSchemaManagers() {}

  /**
   * @return the tenants' schema managers in configuration order, so that they are initialized and
   *     logged in the order an operator declared them: a {@link LiquibaseSchemaManager} where
   *     {@code auto-ddl} is enabled, a {@link NoopSchemaManager} where the schema is managed
   *     externally
   */
  public static Map<String, RdbmsSchemaManager> fromConfigs(
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
    return Collections.unmodifiableMap(schemaManagers);
  }
}
