/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/**
 * Reports each physical tenant's schema initialization on {@code /actuator/health}. Purely
 * informational: it is deliberately kept out of the liveness, readiness and startup groups, which
 * must never act on one tenant's state because acting on a node affects every tenant on it.
 *
 * <p>A tenant is UP once serviceable, DEGRADED while it is not but nothing has failed for good, and
 * DOWN once its task stopped without making it serviceable. The indicator is UP when every tenant
 * is, DOWN when every tenant is, and DEGRADED otherwise — the roll-up the gateway's cluster health
 * indicator uses — so that only a node left with no tenant to serve answers 503, and a health check
 * wired to this endpoint cannot restart a node over one tenant that needs an operator.
 */
@NullMarked
public final class PhysicalTenantSchemaInitializationHealthIndicator implements HealthIndicator {

  static final Status DEGRADED = new Status("DEGRADED");

  /**
   * Long enough to name the cause, short enough that the endpoint stays readable with many tenants:
   * a mapping-validation failure lists every differing field of every index, and is logged in full
   * where it happens.
   */
  static final int MAX_ERROR_LENGTH = 256;

  static final String TRUNCATION_SUFFIX = "... (see logs)";

  private final Supplier<Map<String, SchemaInitializationStatus>> statuses;

  public PhysicalTenantSchemaInitializationHealthIndicator(
      final Supplier<Map<String, SchemaInitializationStatus>> statuses) {
    this.statuses = statuses;
  }

  @Override
  public Health health() {
    final var statusesByTenant = statuses.get();
    var allUp = true;
    var allDown = !statusesByTenant.isEmpty();
    final var details = new LinkedHashMap<String, Object>();
    for (final var tenant : statusesByTenant.entrySet()) {
      final var status = tenant.getValue();
      final var tenantStatus = statusOf(status.state());
      allUp &= Status.UP.equals(tenantStatus);
      allDown &= Status.DOWN.equals(tenantStatus);
      details.put(tenant.getKey(), detailsOf(tenantStatus, status));
    }

    final Health.Builder health;
    if (allUp) {
      health = Health.up();
    } else if (allDown) {
      health = Health.down();
    } else {
      health = Health.status(DEGRADED);
    }
    return health.withDetails(details).build();
  }

  private static Status statusOf(final SchemaInitializationStatus.State state) {
    return switch (state) {
      case INITIALIZED -> Status.UP;
      case INITIALIZING, RETRYING, RECOVERING -> DEGRADED;
      case FAILED, GAVE_UP, ABORTED -> Status.DOWN;
    };
  }

  /**
   * The failure is rendered the way Spring renders an indicator's own exception, class and message
   * without the stack trace, cut to {@link #MAX_ERROR_LENGTH}. A serviceable tenant reports neither
   * count nor failure: both describe attempts that no longer matter.
   */
  private static Map<String, Object> detailsOf(
      final Status tenantStatus, final SchemaInitializationStatus status) {
    final var details = new LinkedHashMap<String, Object>();
    details.put("status", tenantStatus.getCode());
    details.put("state", status.state().name());
    if (status.state() == SchemaInitializationStatus.State.INITIALIZED) {
      return details;
    }
    if (status.failedAttempts() > 0) {
      details.put("failedAttempts", status.failedAttempts());
    }
    final var failure = status.lastFailure();
    if (failure != null) {
      details.put("error", truncate(failure.getClass().getName() + ": " + failure.getMessage()));
    }
    return details;
  }

  private static String truncate(final String error) {
    if (error.length() <= MAX_ERROR_LENGTH) {
      return error;
    }
    return error.substring(0, MAX_ERROR_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
  }
}
