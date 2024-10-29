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
import java.util.Optional;

public class ProcessCacheImpl implements ProcessCache {

  private final LoadingCache<Long, CachedProcessEntity> cache;

  public ProcessCacheImpl(
      final long maxSize, final CacheLoader<Long, CachedProcessEntity> cacheLoader) {
    cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
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
  public Optional<CachedProcessEntity> get(final long processDefinitionKey) {
    return Optional.ofNullable(cache.get(processDefinitionKey));
  }

  @Override
  public void put(final long processDefinitionKey, final CachedProcessEntity processEntity) {
    cache.put(processDefinitionKey, processEntity);
  }

  @Override
  public void remove(final long processDefinitionKey) {
    cache.invalidate(processDefinitionKey);
  }

  @Override
  public void clear() {
    cache.invalidateAll();
  }
}
