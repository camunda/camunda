/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One {@link InternalConfigRefresher} per physical tenant, each run at the refresh interval of its
 * tenant on one shared thread. A run is a read of the store and, rarely, a client swap, so one
 * thread serves many tenants; a slow store or a long drain delays the next run of the others.
 */
public final class InternalConfigRefreshers implements AutoCloseable {

  private final Map<String, InternalConfigRefresher> byPhysicalTenant = new LinkedHashMap<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("internal-config-refresh").daemon().factory());

  private InternalConfigRefreshers() {}

  public static InternalConfigRefreshers of(
      final InternalConfigSecrets secrets,
      final Map<String, Duration> intervals,
      final SecretRefreshHealth health) {
    final var refreshers = new InternalConfigRefreshers();
    intervals.forEach(
        (tenantId, interval) -> {
          final var refresher =
              new InternalConfigRefresher(secrets.forPhysicalTenant(tenantId), health);
          refreshers.byPhysicalTenant.put(tenantId, refresher);
          refreshers.scheduler.scheduleWithFixedDelay(
              refresher::refresh, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        });
    return refreshers;
  }

  /** No tenant refreshes, for an application that does not build the internal-config stores. */
  public static InternalConfigRefreshers none() {
    return new InternalConfigRefreshers();
  }

  public Optional<InternalConfigRefresher> forPhysicalTenant(final String physicalTenantId) {
    return Optional.ofNullable(byPhysicalTenant.get(physicalTenantId));
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
