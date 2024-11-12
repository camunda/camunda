/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import io.camunda.exporter.cache.process.CachedProcessEntity;
import java.util.HashMap;
import java.util.Optional;

public class TestProcessCache implements ExporterEntityCache<Long, CachedProcessEntity> {

  private final HashMap<Long, CachedProcessEntity> cache = new HashMap<>();

  @Override
  public Optional<CachedProcessEntity> get(final Long entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  @Override
  public void put(final Long entityKey, final CachedProcessEntity processEntity) {
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
