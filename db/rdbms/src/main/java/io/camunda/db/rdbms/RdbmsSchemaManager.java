/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

/**
 * Manages the RDBMS schema of a single physical tenant.
 *
 * @see LiquibaseSchemaManager runs Liquibase migrations ({@code auto-ddl=true})
 * @see NoopSchemaManager skips migration for externally managed schemas ({@code auto-ddl=false})
 */
public interface RdbmsSchemaManager {

  /**
   * Initializes the schema (e.g. runs Liquibase migrations). Called once at startup. Throwing
   * aborts startup.
   */
  void initialize() throws Exception;

  /**
   * Returns {@code true} once the schema has been fully initialized so that the RDBMS exporter may
   * open against it.
   */
  boolean isInitialized();
}
