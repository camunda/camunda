/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import io.camunda.exporter.cache.treePath.CachedTreePathKey;
import java.util.HashMap;
import java.util.Optional;

public class TestIntraTreePathCache implements ExporterEntityCache<CachedTreePathKey, String> {

  private final HashMap<CachedTreePathKey, String> cache = new HashMap<>();

  @Override
  public Optional<String> get(final CachedTreePathKey entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  @Override
  public void put(final CachedTreePathKey entityKey, final String treePath) {
    cache.put(entityKey, treePath);
  }

  @Override
  public void remove(final CachedTreePathKey entityKey) {
    cache.remove(entityKey);
  }

  @Override
  public void clear() {
    cache.clear();
  }
}
