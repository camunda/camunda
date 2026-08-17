/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the {@link SecretCache} one store caches in, given the ID that store is configured under.
 *
 * <p>{@link SecretStoreRegistry} calls this at most once per configured store, and only for a store
 * that is actually wrapped in a {@link CachingSecretStore} — a store that caches natively is left
 * as it is, so no cache is built for it. That is what keeps a caller instrumenting the caches it
 * hands out from publishing meters for a store no cache of theirs ever serves.
 *
 * <p>Must return a fresh cache per store ID: a cache is keyed by the bare secret name, so an
 * instance shared by two stores would let one store's value answer for another store's secret of
 * the same name.
 */
@FunctionalInterface
@NullMarked
public interface SecretCacheFactory {

  /**
   * Returns the cache the store under {@code storeId} holds what it resolves in.
   *
   * @param storeId the ID the store is configured under, unique within one physical tenant
   */
  SecretCache create(String storeId);

  /**
   * Returns a factory building a {@link CaffeineSecretCache} bounded by the given size and TTL for
   * every store ID, publishing what each one does on the given registry tagged with that store ID.
   *
   * <p>The sole place that hands a {@link MeterRegistry} to a {@link CaffeineSecretCache}: that
   * class itself only knows {@link SecretCacheMetrics}, so a caller outside this package cannot
   * construct one directly and reaches the cache through here instead.
   */
  static SecretCacheFactory metered(
      final int maxSize,
      final Duration ttl,
      final InstantSource timeSource,
      final MeterRegistry meterRegistry) {
    return storeId ->
        CaffeineSecretCache.create(
            maxSize, ttl, timeSource, new SecretCacheMetrics(meterRegistry, storeId));
  }
}
