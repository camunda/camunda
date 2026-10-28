/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.application.commons.pt.PhysicalTenantSchemaInitializationHealthIndicator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/**
 * Reports on {@code /actuator/health} which physical tenants failed to apply their latest secrets.
 * Like the schema initialization indicator it is in no probe group: one tenant on old credentials
 * must not restart a node that serves every tenant.
 */
public final class SecretRefreshHealthIndicator implements SecretRefreshHealth, HealthIndicator {

  private final Map<String, Map<String, String>> failuresByTenant = new ConcurrentHashMap<>();

  @Override
  public void refreshed(final String physicalTenantId, final String consumer) {
    failuresByTenant
        .computeIfAbsent(physicalTenantId, id -> new ConcurrentHashMap<>())
        .remove(consumer);
  }

  @Override
  public void failed(final String physicalTenantId, final String consumer, final String reason) {
    failuresByTenant
        .computeIfAbsent(physicalTenantId, id -> new ConcurrentHashMap<>())
        .put(consumer, reason);
  }

  @Override
  public Health health() {
    final var details = new LinkedHashMap<String, Object>();
    var degraded = false;
    for (final var tenant : failuresByTenant.entrySet()) {
      if (tenant.getValue().isEmpty()) {
        details.put(tenant.getKey(), Map.of("status", Status.UP.getCode()));
      } else {
        degraded = true;
        details.put(
            tenant.getKey(),
            Map.of(
                "status",
                PhysicalTenantSchemaInitializationHealthIndicator.DEGRADED.getCode(),
                "failures",
                Map.copyOf(tenant.getValue())));
      }
    }
    return Health.status(
            degraded ? PhysicalTenantSchemaInitializationHealthIndicator.DEGRADED : Status.UP)
        .withDetails(details)
        .build();
  }
}
