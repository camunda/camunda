/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.search.schema.SchemaManagerContainer;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public class SchemaReadinessCheck implements HealthIndicator {

  public static final String SCHEMA_READINESS_CHECK = "schemaReadinessCheck";
  private final SchemaManagerContainer schemaManagerContainer;

  public SchemaReadinessCheck(final SchemaManagerContainer schemaManagerContainer) {
    this.schemaManagerContainer = schemaManagerContainer;
  }

  @Override
  public Health health() {
    return (schemaManagerContainer.isInitialized() ? Health.up() : Health.down()).build();
  }
}
