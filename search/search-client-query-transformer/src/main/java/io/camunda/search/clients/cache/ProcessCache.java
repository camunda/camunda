/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Process cache uses a Caffeine {@link Cache} to store process definition metadata.
 *
 * <p>This is a simplified cache intended for enriching search read results.
 *
 * <p>TODO: Unify this cache implementation with the service-layer process cache.
 *
 * <p>Tracking: {@link <a
 * href="https://github.com/camunda/camunda/issues/43660">camunda/camunda#43660</a>}
 */
public final class ProcessCache {

  private final Cache<Long, ProcessCacheItem> cache;
  private final ProcessDefinitionProvider provider;

  public ProcessCache(
      final Configuration configuration, final SearchClientBasedQueryExecutor executor) {
    provider = new ProcessDefinitionProvider(executor);

    final var cacheBuilder = Caffeine.newBuilder().maximumSize(configuration.maxSize());

    final var expirationIdle = configuration.expirationIdleMillis();
    if (expirationIdle != null && expirationIdle > 0) {
      cacheBuilder.expireAfterAccess(expirationIdle, TimeUnit.MILLISECONDS);
    }

    cache = cacheBuilder.build();
  }

  public ProcessCacheItem getCacheItem(final long processDefinitionKey) {
    final ProcessCacheItem cached = cache.getIfPresent(processDefinitionKey);
    if (cached != null) {
      return cached;
    }

    final ProcessCacheItem loaded = provider.extractProcessData(processDefinitionKey);
    if (loaded != null && !loaded.isEmpty()) {
      cache.put(processDefinitionKey, loaded);
      return loaded;
    }

    return ProcessCacheItem.EMPTY;
  }

  public ProcessCacheResult getCacheItems(final Set<Long> processDefinitionKeys) {
    final Map<Long, ProcessCacheItem> result = new HashMap<>(processDefinitionKeys.size());

    final Map<Long, ProcessCacheItem> cached = cache.getAllPresent(processDefinitionKeys);
    result.putAll(cached);

    final Set<Long> missingKeys =
        processDefinitionKeys.stream()
            .filter(k -> !cached.containsKey(k))
            .collect(java.util.stream.Collectors.toSet());

    if (!missingKeys.isEmpty()) {
      final Map<Long, ProcessCacheItem> loaded = provider.extractProcessData(missingKeys);

      loaded.forEach(
          (k, item) -> {
            if (item != null && !item.isEmpty()) {
              cache.put(k, item);
              result.put(k, item);
            }
          });
    }

    processDefinitionKeys.forEach(k -> result.putIfAbsent(k, ProcessCacheItem.EMPTY));

    return new ProcessCacheResult(result);
  }

  public Cache<Long, ProcessCacheItem> getRawCache() {
    return cache;
  }

  public record Configuration(long maxSize, Long expirationIdleMillis) {
    public static Configuration getDefault() {
      return new Configuration(1_000, null);
    }
  }
}
