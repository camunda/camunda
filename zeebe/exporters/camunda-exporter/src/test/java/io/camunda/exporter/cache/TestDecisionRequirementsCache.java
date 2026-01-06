/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestDecisionRequirementsCache
    implements ExporterEntityCache<Long, CachedDecisionRequirementsEntity> {
  private final HashMap<Long, CachedDecisionRequirementsEntity> cache = new HashMap<>();

  @Override
  public Optional<CachedDecisionRequirementsEntity> get(final Long entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  @Override
  public Map<Long, CachedDecisionRequirementsEntity> getAll(final Iterable<Long> keys) {
    final Map<Long, CachedDecisionRequirementsEntity> map = new HashMap<>();
    keys.forEach(k -> map.put(k, get(k).orElse(null)));
    return map;
  }

  @Override
  public void put(final Long entityKey, final CachedDecisionRequirementsEntity processEntity) {
    cache.put(entityKey, processEntity);
  }

  @Override
  public void remove(final Long entityKey) {
    cache.remove(entityKey);
  }

  @Override
  public void clear() {
    cache.clear();
  }
}
