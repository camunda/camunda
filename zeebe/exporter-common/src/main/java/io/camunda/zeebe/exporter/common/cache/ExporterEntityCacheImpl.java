/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            .build(wrapCacheLoader(cacheLoader));
  }

  public ExporterEntityCacheImpl(
      final long maxSize,
      final BulkExporterEntityCacheLoader<K, T> cacheLoader,
      final CaffeineCacheStatsCounter statsCounter) {
    cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .recordStats(() -> statsCounter)
            .build(wrapCacheLoader(cacheLoader));
  }

  @Override
  public Optional<T> get(final K entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  @Override
  public Map<K, T> getAll(final Iterable<K> keys) {
    return cache.getAll(keys);
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

  private static <K, T> CacheLoader<K, T> wrapCacheLoader(final CacheLoader<K, T> delegate) {
    return new CacheLoader<>() {
      @Override
      public T load(final K key) {
        return loadValue(delegate, key);
      }

      @Override
      public Map<? extends K, ? extends T> loadAll(final Set<? extends K> keys) {
        final Map<K, T> entries = new HashMap<>();
        for (final K key : keys) {
          final var value = load(key);
          if (value != null) {
            entries.put(key, value);
          }
        }
        return entries;
      }
    };
  }

  private static <K, T> BulkExporterEntityCacheLoader<K, T> wrapCacheLoader(
      final BulkExporterEntityCacheLoader<K, T> delegate) {
    return new BulkExporterEntityCacheLoader<>() {
      @Override
      public T load(final K key) {
        return loadValue(delegate, key);
      }

      @Override
      public Map<? extends K, ? extends T> loadAll(final Set<? extends K> keys) {
        try {
          return delegate.loadAll(keys);
        } catch (final CacheLoaderFailedException e) {
          throw e;
        } catch (final Exception e) {
          throw new CacheLoaderFailedException(e);
        }
      }
    };
  }

  private static <K, T> T loadValue(final CacheLoader<K, T> delegate, final K key) {
    try {
      return delegate.load(key);
    } catch (final CacheLoaderFailedException e) {
      throw e;
    } catch (final Exception e) {
      throw new CacheLoaderFailedException(e);
    }
  }
}
