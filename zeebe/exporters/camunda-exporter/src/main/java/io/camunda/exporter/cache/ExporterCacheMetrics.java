/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

public class ExporterCacheMetrics implements StatsCounter {

  private static final String NAMESPACE = "zeebe.camunda.exporter.processcache";

  private final Counter hitCount;
  private final Counter missCount;
  private final Timer loadSuccessDuration;
  private final Timer loadFailureDuration;
  private final Counter evictionCount;
  private final String cacheName;

  public ExporterCacheMetrics(final String cacheName, final MeterRegistry meterRegistry) {
    this.cacheName = cacheName;
    hitCount =
        Counter.builder(meterName("hits"))
            .description("Number of cache hits")
            .register(meterRegistry);

    missCount =
        Counter.builder(meterName("misses"))
            .description("Number of cache misses")
            .register(meterRegistry);

    evictionCount =
        Counter.builder(meterName("evictions"))
            .description("Number of cache evictions")
            .register(meterRegistry);

    loadSuccessDuration =
        Timer.builder(meterName("load.duration.success"))
            .description("The time the cache spent computing or retrieving the new value")
            .publishPercentileHistogram()
            .register(meterRegistry);

    loadFailureDuration =
        Timer.builder(meterName("load.duration.failure"))
            .description(
                "The time the cache spent computing or retrieving the new value prior to discovering the value doesn't exist or an exception being thrown")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  @Override
  public void recordHits(final int count) {
    hitCount.increment(count);
  }

  @Override
  public void recordMisses(final int count) {
    missCount.increment(count);
  }

  @Override
  public void recordLoadSuccess(final long loadTime) {
    loadSuccessDuration.record(loadTime, TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordLoadFailure(final long loadTime) {
    loadFailureDuration.record(loadTime, TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordEviction(final int weight, final RemovalCause cause) {
    evictionCount.increment();
  }

  @Override
  public CacheStats snapshot() {
    // not implemented, as we don't need it
    return null;
  }

  private String meterName(final String name) {
    return NAMESPACE + "." + cacheName + "." + name;
  }
}
