/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import liquibase.integration.spring.MultiTenantSpringLiquibase;
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

  @Override
  public void afterPropertiesSet() throws Exception {
    super.afterPropertiesSet();
    initialized = true;
    LOG.debug("Liquibase migrations completed.");
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }
}
