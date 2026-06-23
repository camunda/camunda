/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.db.rdbms.LiquibaseScriptGenerator;
import io.camunda.db.rdbms.LiquibaseScriptGenerator.DatabaseVersion;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import liquibase.exception.LiquibaseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

/**
 * Bypasses Liquibase by pre-generating DDL SQL once per JVM (cached by databaseId) and executing it
 * directly. Significantly faster than Liquibase for databases with expensive precondition checks
 * (e.g. Oracle).
 *
 * <p>Runs the DDL against every physical tenant's datasource (with that tenant's table prefix), so
 * the fast init also works under physical-tenant mode where the application is configured with a
 * dedicated per-tenant secondary store.
 */
public class ScriptBasedSchemaManager implements RdbmsSchemaManagerRegistry, InitializingBean {
  private static final Logger LOGGER = LoggerFactory.getLogger(ScriptBasedSchemaManager.class);

  private static final ConcurrentHashMap<String, String> SCRIPT_CACHE = new ConcurrentHashMap<>();

  private static final String CHANGELOG = "db/changelog/rdbms-exporter/changelog-master.xml";
  private static final String PREFIX_PLACEHOLDER = "__PREFIX__";

  private final RdbmsDataSources rdbmsDataSources;
  private final Environment environment;
  private final String defaultPrefix;

  private final Set<String> initializedTenants = ConcurrentHashMap.newKeySet();

  public ScriptBasedSchemaManager(
      final RdbmsDataSources rdbmsDataSources,
      final Environment environment,
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String defaultPrefix) {
    this.rdbmsDataSources = rdbmsDataSources;
    this.environment = environment;
    this.defaultPrefix = defaultPrefix;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    for (final String physicalTenantId : rdbmsDataSources.physicalTenantIds()) {
      initializeTenant(physicalTenantId);
    }
  }

  private void initializeTenant(final String physicalTenantId) throws Exception {
    final VendorDatabaseProperties vendorDatabaseProperties =
        rdbmsDataSources.vendorPropertiesFor(physicalTenantId);
    final DataSource dataSource = rdbmsDataSources.dataSourceFor(physicalTenantId);
    final String prefix = prefixFor(physicalTenantId);
    LOGGER.info(
        "Initializing schema for physical tenant '{}' with prefix '{}'", physicalTenantId, prefix);

    final String databaseId = vendorDatabaseProperties.databaseId();
    final String template =
        SCRIPT_CACHE.computeIfAbsent(
            databaseId,
            id -> {
              final DatabaseVersion dbVersion =
                  LiquibaseScriptGenerator.getDatabaseVersion(databaseId);
              try {
                return LiquibaseScriptGenerator.generateSqlScript(
                    dbVersion,
                    CHANGELOG,
                    PREFIX_PLACEHOLDER,
                    vendorDatabaseProperties.userCharColumnSize(),
                    vendorDatabaseProperties.errorMessageSize(),
                    vendorDatabaseProperties.treePathSize());
              } catch (final LiquibaseException e) {
                throw new IllegalStateException(
                    "Failed to generate SQL script for database '" + id + "'", e);
              }
            });

    final String sql =
        template
            .replace(PREFIX_PLACEHOLDER, StringUtils.trimToEmpty(prefix))
            .replaceAll("--[^\n]*(\n|$)", ""); // strip single-line comments before splitting

    try (final var conn = dataSource.getConnection()) {
      for (final String stmt : sql.split(";")) {
        final String trimmed = stmt.strip();
        if (!trimmed.isEmpty()) {
          LOGGER.info(
              "Executing DDL statement: {}...",
              trimmed.substring(0, Math.min(trimmed.length(), 50)));
          conn.createStatement().execute(trimmed);
          conn.commit();
        }
      }
    }

    initializedTenants.add(physicalTenantId);
  }

  private String prefixFor(final String physicalTenantId) {
    if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      return defaultPrefix;
    }
    return environment.getProperty(
        "camunda.physical-tenants." + physicalTenantId + ".data.secondary-storage.rdbms.prefix",
        "");
  }

  @Override
  public boolean isInitialized(final String physicalTenantId) {
    return initializedTenants.contains(physicalTenantId);
  }
}
