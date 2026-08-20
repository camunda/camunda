/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

/**
 * A no-operation {@link RdbmsSchemaManager} for physical tenants with {@code auto-ddl=false}.
 *
 * <p>The schema is managed externally (by the operator), so no migration is run. The tenant is
 * always reported as initialized so the RDBMS exporter can open against the externally managed
 * schema.
 */
public class NoopSchemaManager implements RdbmsSchemaManager {

  @Override
  public void initialize() {
    // no-op: the schema is managed externally
  }

  @Override
  public boolean isInitialized() {
    return true;
  }
}
