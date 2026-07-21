/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.cluster.SecondaryStorageAvailability;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports the node as ready once at least one physical tenant's secondary storage is available — a
 * node stays ready as long as it can serve at least one physical tenant, rather than requiring
 * every tenant to be initialized. No per-tenant detail is exposed on this probe.
 */
public class SchemaReadinessCheck implements HealthIndicator {

  public static final String SCHEMA_READINESS_CHECK = "schemaReadinessCheck";
  private final SecondaryStorageAvailability secondaryStorageAvailability;

  public SchemaReadinessCheck(final SecondaryStorageAvailability secondaryStorageAvailability) {
    this.secondaryStorageAvailability = secondaryStorageAvailability;
  }

  @Override
  public Health health() {
    return (secondaryStorageAvailability.anyAvailable() ? Health.up() : Health.down()).build();
  }
}
