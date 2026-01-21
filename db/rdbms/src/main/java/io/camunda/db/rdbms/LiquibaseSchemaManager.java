/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the database schema using Liquibase for multi-tenant applications.
 *
 * <p>This class extends {@link MultiTenantSpringLiquibase} to leverage its capabilities for
 * managing database migrations in a multi-tenant environment. It also implements the {@link
 * RdbmsSchemaManager} interface to provide a method for checking if the schema has been
 * initialized.
 */
public class LiquibaseSchemaManager extends MultiTenantSpringLiquibase
    implements RdbmsSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(LiquibaseSchemaManager.class);

  private volatile boolean initialized = false;
  private Map<String, DataSource> engineDataSources = new HashMap<>();

  public void setEngineDataSources(final Map<String, DataSource> engineDataSources) {
    this.engineDataSources = engineDataSources;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    super.afterPropertiesSet();

    for (final var entry : engineDataSources.entrySet()) {
      final var engineName = entry.getKey();
      final var dataSource = entry.getValue();
      LOG.info("Initializing Liquibase for engine '{}'", engineName);

      final var liquibase = new SpringLiquibase();
      liquibase.setDataSource(dataSource);
      liquibase.setChangeLog(getChangeLog());
      liquibase.setChangeLogParameters(getParameters());
      liquibase.setDatabaseChangeLogTable(getDatabaseChangeLogTable());
      liquibase.setDatabaseChangeLogLockTable(getDatabaseChangeLogLockTable());
      liquibase.afterPropertiesSet();
    }

    initialized = true;
    LOG.debug("Liquibase migrations completed.");
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }
}
