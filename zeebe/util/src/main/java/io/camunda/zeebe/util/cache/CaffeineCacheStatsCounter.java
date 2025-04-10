/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.cache;

import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

public class CaffeineCacheStatsCounter implements StatsCounter {

  public static final String TAG_TYPE = "type";
  private final Timer loadSuccessDuration;
  private final Timer loadFailureDuration;
  private final Counter evictionCount;
  private final String cacheName;
  private final String namespace;
  private final MeterRegistry meterRegistry;

  public CaffeineCacheStatsCounter(
      final String namespace, final String cacheName, final MeterRegistry meterRegistry) {
    this.cacheName = cacheName;
    this.namespace = namespace;
    this.meterRegistry = meterRegistry;

    Counter.builder(meterName("result"))
        .description("Number of cache access results by type")
        .tag(TAG_TYPE, "")
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
    meterRegistry.counter(meterName("result"), TAG_TYPE, CacheResult.HIT.name()).increment(count);
  }

  @Override
  public void recordMisses(final int count) {
    meterRegistry.counter(meterName("result"), TAG_TYPE, CacheResult.MISS.name()).increment(count);
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
    return namespace + "." + cacheName + "." + name;
  }

  enum CacheResult {
    /** Entry was found in the cache */
    HIT,
    /** Entry was not found in the cache */
    MISS
  }
}
