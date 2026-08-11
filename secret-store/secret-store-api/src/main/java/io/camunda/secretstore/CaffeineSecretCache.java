/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The production default {@link SecretCache}: a value expires {@link #DEFAULT_TTL} after it was
 * cached, so a secret rotated in the store is picked up without a restart, and the cache never
 * grows past {@link #DEFAULT_MAX_SIZE} entries. One instance is created per configured store (see
 * {@link SecretStoreRegistry}), so the bound is per store, and that same construction is what keeps
 * entries of different stores apart.
 *
 * <p>Expiry is driven by an injected {@link InstantSource} rather than the wall clock directly, so
 * a test can advance time instead of sleeping, and so a controlled clock (as {@code
 * /actuator/clock} mutates in production) reaches the cache. Caffeine expects a monotonic ticker: a
 * source that steps backward (an NTP correction, or a controlled clock reset after a forward {@code
 * /actuator/clock} jump) can make an entry live longer than the TTL, or in a narrow window let an
 * already-expired one be served once more before Caffeine's own asynchronous removal catches up.
 * The bound on this is what matters: it can only delay expiry, never corrupt a value, and only a
 * clock movement large enough to rival the TTL can trigger it — ordinary wall-clock drift can't, so
 * this only bites the controlled-clock, {@code /actuator/clock}-driven tests and tooling that this
 * feature itself exists to support, not the default uncontrolled clock production runs on.
 *
 * <p>Eviction of expired or excess entries happens asynchronously; {@link #cleanUp()} forces it
 * synchronously, which is only useful in tests that need to observe the bound immediately.
 *
 * <p>What the cache does is published on {@link SecretCacheMetricsDoc}'s meters, tagged with the
 * store the cache belongs to; the factories that take no {@link MeterRegistry} publish nothing.
 */
public final class CaffeineSecretCache implements SecretCache {

  public static final Duration DEFAULT_TTL = Duration.ofMinutes(20);
  public static final int DEFAULT_MAX_SIZE = 1000;

  /**
   * The store ID a cache publishing nothing is tagged with. Never reaches a metrics endpoint, and
   * deliberately not spellable as a store ID — those are property-path segments under {@code
   * camunda.secrets.stores.<type>.<id>} — so it cannot be mistaken for a configured store if one
   * ever does.
   */
  private static final String UNMETERED_STORE_ID = "<unmetered>";

  private final Cache<String, String> cache;
  private final SecretCacheMetrics metrics;

  private CaffeineSecretCache(final Cache<String, String> cache, final SecretCacheMetrics metrics) {
    this.cache = cache;
    this.metrics = metrics;
  }

  /**
   * Creates a cache bounded by the given size and expiring entries the given duration after write,
   * publishing what it does on the given registry under the given store ID.
   */
  public static CaffeineSecretCache create(
      final int maxSize,
      final Duration ttl,
      final InstantSource timeSource,
      final MeterRegistry meterRegistry,
      final String storeId) {
    final var metrics = new SecretCacheMetrics(meterRegistry, storeId);
    final Cache<String, String> cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttl)
            .ticker(toTicker(timeSource))
            // hits, misses and the evictions Caffeine performs itself are reported from here, so
            // they need no call site of their own
            .recordStats(() -> metrics)
            .build();
    metrics.registerSizeGauge(cache);
    return new CaffeineSecretCache(cache, metrics);
  }

  /**
   * Creates a cache as the five-argument factory does, but publishing nothing: the meters are
   * registered on a registry that is discarded with the cache.
   */
  public static CaffeineSecretCache create(
      final int maxSize, final Duration ttl, final InstantSource timeSource) {
    return create(maxSize, ttl, timeSource, discardedRegistry(), UNMETERED_STORE_ID);
  }

  /**
   * Creates a new cache with {@link #DEFAULT_MAX_SIZE} and {@link #DEFAULT_TTL}, publishing what it
   * does on the given registry under the given store ID.
   *
   * @return a new cache
   */
  public static CaffeineSecretCache createDefault(
      final InstantSource timeSource, final MeterRegistry meterRegistry, final String storeId) {
    return create(DEFAULT_MAX_SIZE, DEFAULT_TTL, timeSource, meterRegistry, storeId);
  }

  /**
   * Creates a new cache with {@link #DEFAULT_MAX_SIZE} and {@link #DEFAULT_TTL}, publishing
   * nothing.
   *
   * @return a new cache
   */
  public static CaffeineSecretCache createDefault(final InstantSource timeSource) {
    return create(DEFAULT_MAX_SIZE, DEFAULT_TTL, timeSource);
  }

  /**
   * A registry that discards everything registered on it — Micrometer's own way to spell "no
   * metrics". One per cache rather than a shared static, so the meters it holds are collected along
   * with the cache that registered them.
   */
  private static MeterRegistry discardedRegistry() {
    return new CompositeMeterRegistry();
  }

  private static Ticker toTicker(final InstantSource timeSource) {
    return () -> TimeUnit.MILLISECONDS.toNanos(timeSource.millis());
  }

  @Override
  public Optional<String> get(final String name) {
    return Optional.ofNullable(cache.getIfPresent(name));
  }

  @Override
  public void put(final String name, final String value) {
    cache.put(name, value);
  }

  /**
   * Removes the name from the cache, counting an eviction only if a value was actually removed —
   * Caffeine reports the evictions it performs itself but never a removal by name.
   *
   * <p>Goes through the map view rather than {@code invalidate} because that reports whether a
   * value was there, atomically and without recording the hit or miss a lookup would. Both matter:
   * {@link CachingSecretStore} removes a name on every permanent failure from its store, so
   * counting the calls rather than the removals would turn {@link
   * SecretCacheMetricsDoc#CACHE_EVICTIONS} into a count of failing lookups.
   */
  @Override
  public void remove(final String name) {
    if (cache.asMap().remove(name) != null) {
      metrics.recordExplicitEviction();
    }
  }

  /**
   * Performs any pending maintenance operations needed by the cache, including eviction of expired
   * or excess entries. This method is primarily useful in tests to force synchronous eviction,
   * since Caffeine performs eviction asynchronously by default.
   */
  public void cleanUp() {
    cache.cleanUp();
  }
}
