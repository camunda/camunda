/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

public interface MigrationHandler {

  int SIZE = 100;

  void migrate();

  default boolean isConflictError(final Exception e) {
    return e.getMessage().contains("Failed with code 409: 'Conflict'")
        || e.getMessage().contains("the entity is already assigned to the tenant.");
  }
}
