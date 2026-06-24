/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestBatchOperationCache
    implements ExporterEntityCache<String, CachedBatchOperationEntity> {

  private final HashMap<String, CachedBatchOperationEntity> cache = new HashMap<>();

  @Override
  public Optional<CachedBatchOperationEntity> get(final String entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  @Override
  public Map<String, CachedBatchOperationEntity> getAll(final Iterable<String> keys) {
    final Map<String, CachedBatchOperationEntity> map = new HashMap<>();
    keys.forEach(k -> map.put(k, get(k).orElse(null)));
    return map;
  }

  @Override
  public void put(final String entityKey, final CachedBatchOperationEntity entity) {
    cache.put(entityKey, entity);
  }

  @Override
  public void remove(final String entityKey) {
    cache.remove(entityKey);
  }

  @Override
  public void clear() {
    cache.clear();
  }
}
