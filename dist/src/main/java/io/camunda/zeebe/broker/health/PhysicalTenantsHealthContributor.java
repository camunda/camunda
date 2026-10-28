/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.cluster.PhysicalTenant;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantRegistry;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exposes the health of every physical tenant on this node under {@code
 * /actuator/health/physicalTenants/<id>}, read live from the {@link PhysicalTenantRegistry}.
 *
 * <p>It is part of neither the liveness nor the readiness group: one tenant that starts, restarts
 * or failed must not restart the node or take every tenant out of the load balancer. A tenant that
 * does not serve reports {@code OUT_OF_SERVICE} or {@code DOWN}, so its own path answers 503.
 */
@Component
@Profile("broker")
@NullMarked
public final class PhysicalTenantsHealthContributor implements CompositeHealthContributor {

  static final Status DEGRADED = new Status("DEGRADED");

  private final PhysicalTenantRegistry registry;

  public PhysicalTenantsHealthContributor(final PhysicalTenantRegistry registry) {
    this.registry = registry;
  }

  @Override
  public @Nullable HealthContributor getContributor(final String physicalTenantId) {
    return registry.find(physicalTenantId).map(this::indicator).orElse(null);
  }

  @Override
  public Stream<Entry> stream() {
    return registry.tenants().stream().map(tenant -> new Entry(tenant.id(), indicator(tenant)));
  }

  private HealthIndicator indicator(final PhysicalTenant tenant) {
    return () -> {
      final var health =
          registry.health(tenant.id()).orElse(PhysicalTenantHealth.of(State.STARTING));
      final var builder = Health.status(statusOf(health.state()));
      builder.withDetail("state", health.state().name());
      builder.withDetail("incarnationId", tenant.incarnationId().toString());
      if (tenant.name() != null) {
        builder.withDetail("name", tenant.name());
      }
      if (health.detail() != null) {
        builder.withDetail("detail", health.detail());
      }
      return builder.build();
    };
  }

  static Status statusOf(final State state) {
    return switch (state) {
      case HEALTHY -> Status.UP;
      case DEGRADED -> DEGRADED;
      case STARTING, RESTARTING, REMOVED -> Status.OUT_OF_SERVICE;
      case FAILED -> Status.DOWN;
    };
  }
}
