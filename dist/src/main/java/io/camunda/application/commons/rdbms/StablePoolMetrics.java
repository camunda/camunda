/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The HikariCP metrics of a physical tenant's pool, under the names and tags HikariCP uses, but
 * registered once for the tenant rather than once per pool. A released pool is replaced by a new
 * one of the same name; meters registered per pool would keep the closed pool reachable, and be
 * handed back, still reading it, to the new pool. These meters read whichever pool is current.
 */
@NullMarked
final class StablePoolMetrics implements MetricsTrackerFactory {

  private static final String PREFIX = "hikaricp.connections";

  private final Timer acquire;
  private final Timer usage;
  private final Timer creation;
  private final Counter timeouts;
  private volatile @Nullable PoolStats current;

  StablePoolMetrics(final String poolName, final MeterRegistry registry) {
    acquire =
        Timer.builder(PREFIX + ".acquire")
            .description("Connection acquire time")
            .tag("pool", poolName)
            .register(registry);
    usage =
        Timer.builder(PREFIX + ".usage")
            .description("Connection usage time")
            .tag("pool", poolName)
            .register(registry);
    creation =
        Timer.builder(PREFIX + ".creation")
            .description("Connection creation time")
            .tag("pool", poolName)
            .register(registry);
    timeouts =
        Counter.builder(PREFIX + ".timeout")
            .description("Connection timeout total count")
            .tag("pool", poolName)
            .register(registry);
    gauge(PREFIX, "Total connections", PoolStats::getTotalConnections, poolName, registry);
    gauge(PREFIX + ".idle", "Idle connections", PoolStats::getIdleConnections, poolName, registry);
    gauge(
        PREFIX + ".active",
        "Active connections",
        PoolStats::getActiveConnections,
        poolName,
        registry);
    gauge(PREFIX + ".pending", "Pending threads", PoolStats::getPendingThreads, poolName, registry);
    gauge(PREFIX + ".max", "Max connections", PoolStats::getMaxConnections, poolName, registry);
    gauge(PREFIX + ".min", "Min connections", PoolStats::getMinConnections, poolName, registry);
  }

  @Override
  public IMetricsTracker create(final String poolName, final PoolStats poolStats) {
    current = poolStats;
    return new IMetricsTracker() {
      @Override
      public void recordConnectionCreatedMillis(final long millis) {
        creation.record(millis, TimeUnit.MILLISECONDS);
      }

      @Override
      public void recordConnectionAcquiredNanos(final long nanos) {
        acquire.record(nanos, TimeUnit.NANOSECONDS);
      }

      @Override
      public void recordConnectionUsageMillis(final long millis) {
        usage.record(millis, TimeUnit.MILLISECONDS);
      }

      @Override
      public void recordConnectionTimeout() {
        timeouts.increment();
      }

      @Override
      public void close() {
        if (current == poolStats) {
          current = null;
        }
      }
    };
  }

  private void gauge(
      final String name,
      final String description,
      final ToIntFunction<PoolStats> value,
      final String poolName,
      final MeterRegistry registry) {
    Gauge.builder(
            name,
            this,
            metrics -> {
              final var stats = metrics.current;
              return stats == null ? 0 : value.applyAsInt(stats);
            })
        .description(description)
        .tag("pool", poolName)
        .register(registry);
  }
}
