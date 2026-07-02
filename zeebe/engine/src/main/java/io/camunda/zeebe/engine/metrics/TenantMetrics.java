/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class TenantMetrics {

  private static final String ORGANIZATION_ID =
      System.getenv().getOrDefault("CAMUNDA_CLOUD_ORGANIZATION_ID", "null");

<<<<<<< HEAD
  /**
   * Accumulates every distinct tenant ID seen across export intervals since broker start. Access is
   * single-threaded: only the stream-processor thread writes or reads this set. In SaaS the
   * cardinality is bounded by the number of tenants in an organization (typically in the tens to
   * low hundreds), so unbounded growth is not a practical concern.
   */
  private final Set<String> cumulativeTenants = new HashSet<>();

  /**
   * Mirrors {@link #cumulativeTenants}'s size as a volatile value so the Prometheus scrape thread
   * can read it safely. {@code AtomicInteger.set()} is a volatile write that happens-before {@code
   * AtomicInteger.get()}, guaranteeing the scrape thread never sees a stale or partially-written
   * count. {@link #cumulativeTenants} itself is never read by the scrape thread.
   */
=======
  // Accumulates every distinct tenant ID seen across export intervals since broker start.
  // Access is single-threaded: only the stream-processor thread writes or reads this set.
  // In SaaS the cardinality is bounded by the number of tenants in an organization (typically
  // in the tens to low hundreds), so unbounded growth is not a practical concern.
  private final Set<String> cumulativeTenants = new HashSet<>();
  // Mirrors cumulativeTenants.size() as a volatile value so the Prometheus scrape thread can
  // read it safely. AtomicInteger.set() volatile-write happens-before AtomicInteger.get(),
  // guaranteeing the scrape thread never sees a stale or partially-written count.
  // cumulativeTenants itself is never read by the scrape thread.
>>>>>>> aa0c3b2a (feat: expose zeebe.active.tenants.count Prometheus gauge)
  private final AtomicInteger activeTenantCount = new AtomicInteger(0);

  public TenantMetrics(final MeterRegistry registry) {
    final var meterDoc = EngineMetricsDoc.ACTIVE_TENANTS;
    Gauge.builder(meterDoc.getName(), activeTenantCount, AtomicInteger::get)
        .description(meterDoc.getDescription())
        .tag(EngineKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
        .register(registry);
  }

  /**
   * Records all tenant IDs seen in a completed export interval. Must be called from the
   * stream-processor thread only.
   */
  public void tenantsSeen(final Collection<String> tenantIds) {
    final int before = cumulativeTenants.size();
    cumulativeTenants.addAll(tenantIds);
    if (cumulativeTenants.size() != before) {
      activeTenantCount.set(cumulativeTenants.size());
    }
  }
}
