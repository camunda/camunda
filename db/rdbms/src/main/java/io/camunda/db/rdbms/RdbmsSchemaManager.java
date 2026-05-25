/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

/**
 * Interface for managing RDBMS database schemas on a per-physical-tenant basis.
 *
 * <p>Each physical tenant has its own data source and its own Liquibase schema. The exporter
 * consults this interface, scoped to a single tenant id, before exporting records to make sure the
 * target schema is ready.
 */
public interface RdbmsSchemaManager {

  /**
   * Returns {@code true} if the schema for the given physical tenant has been fully initialized
   * (i.e. Liquibase migrations completed successfully, or {@code auto-ddl} is disabled for that
   * tenant). Returns {@code false} for unknown tenants and for tenants whose migration has not yet
   * finished or failed.
   */
  boolean isInitialized(String physicalTenantId);
}
