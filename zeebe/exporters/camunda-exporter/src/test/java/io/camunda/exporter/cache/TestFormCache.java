/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import io.camunda.exporter.cache.form.CachedFormEntity;
import java.util.HashMap;
import java.util.Optional;

public class TestFormCache implements ExporterEntityCache<String, CachedFormEntity> {

  private final HashMap<String, CachedFormEntity> cache = new HashMap<>();

  @Override
  public Optional<CachedFormEntity> get(final String entityKey) {
    return Optional.ofNullable(cache.get(entityKey));
  }

  @Override
  public void put(final String entityKey, final CachedFormEntity processEntity) {
    cache.put(entityKey, processEntity);
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
