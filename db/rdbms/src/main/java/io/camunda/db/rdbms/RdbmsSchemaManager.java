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
 * <p>A single-attempt operation and nothing more: whether the attempt is repeated, how long it
 * waits between tries and whether the tenant counts as ready are the caller's, so that there is one
 * answer to "is this tenant ready" rather than one here and another wherever the retrying happens.
 * {@code RdbmsSchemaInitializer} in the distribution is that caller.
 *
 * @see LiquibaseSchemaManager runs Liquibase migrations ({@code auto-ddl=true})
 * @see NoopSchemaManager skips migration for externally managed schemas ({@code auto-ddl=false})
 */
public interface RdbmsSchemaManager {

  /**
   * Applies the schema in one attempt — for example by running the Liquibase migration — and throws
   * to report failure. Safe to call again after a failure.
   */
  void initialize() throws Exception;
}
