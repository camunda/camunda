/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Optional;

public class ExporterEntityCache<K, T> {

  private static final int DEFAULT_MAX_CACHE_SIZE = 10000;
  private final LoadingCache<K, T> cache;

  public ExporterEntityCache(final CacheLoader<K, T> loader) {
    cache =
        Caffeine.newBuilder()
            .maximumSize(DEFAULT_MAX_CACHE_SIZE)
            .build(
                k -> {
                  try {
                    return loader.load(k);
                  } catch (final Exception e) {
                    throw new CacheLoaderFailedException(e);
                  }
                });
  }

  public Optional<T> get(final K entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  public void put(final K entityKey, final T entity) {
    cache.put(entityKey, entity);
  }

  public void remove(final K entityKey) {
    cache.invalidate(entityKey);
  }

  public void clear() {
    cache.invalidateAll();
  }

  static class CacheLoaderFailedException extends RuntimeException {
    public CacheLoaderFailedException(final Throwable cause) {
      super(cause);
    }
  }
}
