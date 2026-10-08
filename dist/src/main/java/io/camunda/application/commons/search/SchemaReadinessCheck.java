/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.pt.PhysicalTenantSchemaInitializationHealthIndicator;
import io.camunda.application.commons.pt.SchemaInitializationStatus;
import io.camunda.cluster.SecondaryStorageReadiness;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Rolls up the physical tenants' schema initialization like {@link
 * PhysicalTenantSchemaInitializationHealthIndicator}, without per-tenant details.
 */
@NullMarked
public class SchemaReadinessCheck implements HealthIndicator {

  public static final String SCHEMA_READINESS_CHECK = "schemaReadinessCheck";

  private final SecondaryStorageReadiness secondaryStorageReadiness;
  private final Supplier<Map<String, SchemaInitializationStatus>> schemaInitializationStatuses;

  public SchemaReadinessCheck(
      final SecondaryStorageReadiness secondaryStorageReadiness,
      final Supplier<Map<String, SchemaInitializationStatus>> schemaInitializationStatuses) {
    this.secondaryStorageReadiness = secondaryStorageReadiness;
    this.schemaInitializationStatuses = schemaInitializationStatuses;
  }

  @Override
  public Health health() {
    final var statuses = schemaInitializationStatuses.get();
    if (statuses.isEmpty()) {
      return (secondaryStorageReadiness.anyReady() ? Health.up() : Health.down()).build();
    }
    return Health.status(
            PhysicalTenantSchemaInitializationHealthIndicator.rollUp(statuses.values()))
        .build();
  }
}
