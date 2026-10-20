/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.pt.PhysicalTenantSchemaInitializationHealthIndicator;
import io.camunda.cluster.SecondaryStorageReadiness;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports {@link SecondaryStorageReadiness#nodeReadiness()} as UP, DEGRADED, or DOWN, without
 * per-tenant details.
 */
@NullMarked
public class SchemaReadinessCheck implements HealthIndicator {

  public static final String SCHEMA_READINESS_CHECK = "schemaReadinessCheck";
  private final SecondaryStorageReadiness secondaryStorageReadiness;

  public SchemaReadinessCheck(final SecondaryStorageReadiness secondaryStorageReadiness) {
    this.secondaryStorageReadiness = secondaryStorageReadiness;
  }

  @Override
  public Health health() {
    return Health.status(
            PhysicalTenantSchemaInitializationHealthIndicator.statusOf(
                secondaryStorageReadiness.nodeReadiness()))
        .build();
  }
}
