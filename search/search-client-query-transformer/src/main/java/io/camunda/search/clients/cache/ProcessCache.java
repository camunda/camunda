/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;

/**
 * Process cache uses a Caffeine {@link LoadingCache} to store process definition metadata.
 *
 * <p>This is a simplified cache intended for enriching search read results.
 *
 * <p>TODO: Unify this cache implementation with the service-layer process cache.
 *
 * <p>Tracking: {@link <a
 * href="https://github.com/camunda/camunda/issues/43660">camunda/camunda#43660</a>}
 */
public final class ProcessCache {

  private final LoadingCache<Long, ProcessCacheItem> cache;

  public ProcessCache(
      final Configuration configuration, final SearchClientBasedQueryExecutor executor) {
    final var provider = new ProcessDefinitionProvider(executor);

    final var cacheBuilder = Caffeine.newBuilder().maximumSize(configuration.maxSize());

    final var expirationIdle = configuration.expirationIdleMillis();
    if (expirationIdle != null && expirationIdle > 0) {
      cacheBuilder.expireAfterAccess(expirationIdle, TimeUnit.MILLISECONDS);
    }

    cache = cacheBuilder.build(new ProcessCacheLoader(provider));
  }

  public ProcessCacheItem getCacheItem(final long processDefinitionKey) {
    return cache.get(processDefinitionKey);
  }

  public ProcessCacheResult getCacheItems(final Set<Long> processDefinitionKeys) {
    final Map<Long, ProcessCacheItem> loaded = new HashMap<>(cache.getAll(processDefinitionKeys));
    processDefinitionKeys.forEach(key -> loaded.putIfAbsent(key, ProcessCacheItem.EMPTY));
    return new ProcessCacheResult(loaded);
  }

  public void seedCacheItem(final long processDefinitionKey, final ProcessCacheItem item) {
    cache.put(processDefinitionKey, item == null ? ProcessCacheItem.EMPTY : item);
  }

  public LoadingCache<Long, ProcessCacheItem> getRawCache() {
    return cache;
  }

  public record Configuration(long maxSize, Long expirationIdleMillis) {
    public static Configuration getDefault() {
      return new Configuration(1_000, null);
    }
  }

  private record ProcessCacheLoader(ProcessDefinitionProvider provider)
      implements CacheLoader<Long, ProcessCacheItem> {

    @Override
    public ProcessCacheItem load(final Long processDefinitionKey) {
      return provider.extractProcessData(processDefinitionKey);
    }

    @Override
    public @NonNull Map<Long, ProcessCacheItem> loadAll(
        final @NonNull Set<? extends Long> processDefinitionKeys) {
      //noinspection unchecked
      return provider.extractProcessData((Set<Long>) processDefinitionKeys);
    }
  }
}
