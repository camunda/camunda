/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import java.util.HashMap;
import java.util.Optional;

public class TestProcessCache implements ProcessCache {

  private final HashMap<Long, CachedProcessEntity> cache = new HashMap<>();

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
    cache.remove(processDefinitionKey);
  }

  @Override
  public void clear() {
    cache.clear();
  }
}
