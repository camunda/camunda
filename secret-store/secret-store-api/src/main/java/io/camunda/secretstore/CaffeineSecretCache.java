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
 */
public final class CaffeineSecretCache implements SecretCache {

  public static final Duration DEFAULT_TTL = Duration.ofMinutes(20);
  public static final int DEFAULT_MAX_SIZE = 1000;

  private final Cache<String, String> cache;

  private CaffeineSecretCache(final Cache<String, String> cache) {
    this.cache = cache;
  }

  /**
   * Creates a cache bounded by the given size and expiring entries the given duration after write.
   */
  public static CaffeineSecretCache create(
      final int maxSize, final Duration ttl, final InstantSource timeSource) {
    return new CaffeineSecretCache(
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttl)
            .ticker(toTicker(timeSource))
            .build());
  }

  /**
   * Creates a new cache with {@link #DEFAULT_MAX_SIZE} and {@link #DEFAULT_TTL}.
   *
   * @return a new cache
   */
  public static CaffeineSecretCache createDefault(final InstantSource timeSource) {
    return create(DEFAULT_MAX_SIZE, DEFAULT_TTL, timeSource);
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

  @Override
  public void remove(final String name) {
    cache.invalidate(name);
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
