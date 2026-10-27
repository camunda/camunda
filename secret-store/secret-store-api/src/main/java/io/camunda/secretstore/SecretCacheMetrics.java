/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheEvictionCause;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheKeyNames;
import io.camunda.secretstore.SecretCacheMetricsDoc.SecretCacheResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Records what one store's {@link CaffeineSecretCache} does, as the {@link StatsCounter} Caffeine
 * itself drives. Hits, misses and the evictions Caffeine performs on its own therefore need no call
 * site of their own: the cache reports them as it serves lookups.
 *
 * <p>Every meter is registered here in the constructor with its {@code store} tag already fixed. A
 * cache is created once per configured store while the application starts, so there is no tag value
 * discovered at runtime and nothing to bound.
 *
 * <p>{@code CaffeineCacheStatsCounter} does the same job for the exporter and process caches, and
 * is deliberately not reused: it lives in {@code zeebe-util}, whose dependency set is the one
 * {@link SecretCacheMetricsDoc} already declines to pull into this module and every secret store
 * built on it. The two are worth sharing only once that adapter has a home light enough for both —
 * until then a third copy is the thing to avoid, not this one.
 */
final class SecretCacheMetrics implements StatsCounter {

  /**
   * The store ID a counter publishing nothing is tagged with. Never reaches a metrics endpoint, and
   * deliberately not spellable as a store ID — those are property-path segments under {@code
   * camunda.secrets.stores.<type>.<id>} — so it cannot be mistaken for a configured store if one
   * ever does.
   */
  private static final String UNMETERED_STORE_ID = "<unmetered>";

  private final MeterRegistry registry;
  private final String storeId;
  private final Counter hits;
  private final Counter misses;
  private final Map<SecretCacheEvictionCause, Counter> evictions =
      new EnumMap<>(SecretCacheEvictionCause.class);

  SecretCacheMetrics(final MeterRegistry registry, final String storeId) {
    this.registry = registry;
    this.storeId = storeId;
    hits = resultCounter(SecretCacheResult.HIT);
    misses = resultCounter(SecretCacheResult.MISS);
    for (final var cause : SecretCacheEvictionCause.values()) {
      evictions.put(cause, registerEvictionCounter(cause));
    }
  }

  /**
   * A counter that records everything and publishes nothing, for a cache no caller named a registry
   * for. Spelled as a {@link CompositeMeterRegistry} with nothing behind it — Micrometer's own way
   * to say "no metrics" — rather than as a second {@link StatsCounter} implementation, so the cache
   * takes the same code path either way. One per cache rather than a shared static, so the meters
   * it holds are collected along with the cache that registered them.
   */
  static SecretCacheMetrics none() {
    return new SecretCacheMetrics(new CompositeMeterRegistry(), UNMETERED_STORE_ID);
  }

  @Override
  public void recordHits(final int count) {
    hits.increment(count);
  }

  @Override
  public void recordMisses(final int count) {
    misses.increment(count);
  }

  @Override
  public void recordLoadSuccess(final long loadTime) {
    // the secret cache is not a LoadingCache: a miss is answered by the caching store reading the
    // secret store itself, and that read is already timed by camunda.secret.resolution.duration
  }

  @Override
  public void recordLoadFailure(final long loadTime) {
    // see recordLoadSuccess
  }

  @Override
  public void recordEviction(final int weight, final RemovalCause cause) {
    evictionCounter(SecretCacheEvictionCause.from(cause)).increment();
  }

  @Override
  public CacheStats snapshot() {
    // the meters are the readable form of these numbers; nothing asks the cache for its own stats,
    // and keeping a second set of counters just to answer this would double the work per lookup
    return CacheStats.empty();
  }

  /**
   * Records one entry removed by name. Caffeine calls {@link #recordEviction} only for a cause
   * where {@code RemovalCause.wasEvicted()} holds, which excludes removal by name, so {@link
   * CaffeineSecretCache#remove} calls this instead — and only when a value was actually removed.
   */
  void recordExplicitEviction() {
    evictionCounter(SecretCacheEvictionCause.EXPLICIT).increment();
  }

  /**
   * Publishes the cache's entry count. Registered from the cache's factory rather than here,
   * because the gauge needs the built {@link Cache} and this counter is what builds it.
   *
   * <p>Micrometer holds the cache weakly, which is what a gauge over a live object requires: the
   * cache outlives this registration for as long as the store that owns it is in use, and the gauge
   * stops reporting once it does not. Weakly is also what is wanted here, unlike the {@code
   * strongReference(true)} the repository's supplier gauges are built with: a strong reference
   * would pin the cache — and so up to {@link CaffeineSecretCache#DEFAULT_MAX_SIZE} secret values —
   * in memory for as long as the registry holds the meter, outliving the store they were read from.
   */
  void registerSizeGauge(final Cache<?, ?> cache) {
    final var meterDoc = SecretCacheMetricsDoc.CACHE_SIZE;
    Gauge.builder(meterDoc.getName(), cache, Cache::estimatedSize)
        .description(meterDoc.getDescription())
        .tag(SecretCacheKeyNames.STORE.asString(), storeId)
        .register(registry);
  }

  private Counter resultCounter(final SecretCacheResult result) {
    final var meterDoc = SecretCacheMetricsDoc.CACHE_RESULT;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(SecretCacheKeyNames.STORE.asString(), storeId)
        .tag(SecretCacheKeyNames.RESULT.asString(), result.name())
        .register(registry);
  }

  private Counter evictionCounter(final SecretCacheEvictionCause cause) {
    return Objects.requireNonNull(evictions.get(cause));
  }

  private Counter registerEvictionCounter(final SecretCacheEvictionCause cause) {
    final var meterDoc = SecretCacheMetricsDoc.CACHE_EVICTIONS;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(SecretCacheKeyNames.STORE.asString(), storeId)
        .tag(SecretCacheKeyNames.CAUSE.asString(), cause.name())
        .register(registry);
  }
}
