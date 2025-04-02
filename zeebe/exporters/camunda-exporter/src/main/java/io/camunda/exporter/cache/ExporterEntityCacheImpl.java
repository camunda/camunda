/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import java.util.Optional;

public class ExporterEntityCacheImpl<K, T> implements ExporterEntityCache<K, T> {

  private final LoadingCache<K, T> cache;

  public ExporterEntityCacheImpl(
      final long maxSize,
      final CacheLoader<K, T> cacheLoader,
      final CaffeineCacheStatsCounter statsCounter) {
    cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .recordStats(() -> statsCounter)
            .build(
                k -> {
                  try {
                    return cacheLoader.load(k);
                  } catch (final Exception e) {
                    throw new CacheLoaderFailedException(e);
                  }
                });
  }

  @Override
  public Optional<T> get(final K entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  @Override
  public void put(final K entityKey, final T entity) {
    cache.put(entityKey, entity);
  }

  @Override
  public void remove(final K entityKey) {
    cache.invalidate(entityKey);
  }

  @Override
  public void clear() {
    cache.invalidateAll();
  }
}
