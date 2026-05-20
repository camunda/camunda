/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.db.rdbms.LiquibaseScriptGenerator;
import io.camunda.db.rdbms.LiquibaseScriptGenerator.DatabaseVersion;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import liquibase.exception.LiquibaseException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

/**
 * Bypasses Liquibase by pre-generating DDL SQL once per JVM (cached by databaseId) and executing it
 * directly. Significantly faster than Liquibase for databases with expensive precondition checks
 * (e.g. Oracle).
 */
public class ScriptBasedSchemaManager implements RdbmsSchemaManager, InitializingBean {

  private static final ConcurrentHashMap<String, String> SCRIPT_CACHE = new ConcurrentHashMap<>();

  private static final String CHANGELOG = "db/changelog/rdbms-exporter/changelog-master.xml";
  private static final String PREFIX_PLACEHOLDER = "__PREFIX__";

  private final DataSource dataSource;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final String prefix;

  private volatile boolean initialized = false;

  public ScriptBasedSchemaManager(
      final DataSource dataSource,
      final VendorDatabaseProperties vendorDatabaseProperties,
      @Value("${camunda.data.secondary-storage.rdbms.prefix:}") final String prefix) {
    this.dataSource = dataSource;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.prefix = prefix;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
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
          conn.createStatement().execute(trimmed);
        }
      }
    }

    initialized = true;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }
}
