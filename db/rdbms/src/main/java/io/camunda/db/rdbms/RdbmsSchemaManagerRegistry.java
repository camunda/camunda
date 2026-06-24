/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

/**
 * Tenant-scoped view over the per-physical-tenant {@link RdbmsSchemaManager}s. The RDBMS exporter
 * consults this registry, scoped to a single tenant id, before exporting records to make sure the
 * target schema is ready.
 *
 * @see DefaultRdbmsSchemaManagerRegistry the production implementation that initializes every
 *     tenant's schema at startup
 */
public interface RdbmsSchemaManagerRegistry {

  /**
   * Returns {@code true} if the schema for the given physical tenant has been fully initialized.
   * Returns {@code false} for unknown tenants and for tenants whose initialization has not finished
   * or has failed.
   */
  boolean isInitialized(String physicalTenantId);
}
