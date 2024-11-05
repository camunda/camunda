/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import java.util.Optional;

public interface ProcessCache {

  /**
   * Get cached process entity for the given processDefinitionKey. If the process is not cached, it
   * will be loaded from the configured backed. If no processDefinition is found then the returned
   * optional will be empty. If the query to backend fails otherwise, the method will throw an
   * exception.
   *
   * @param processDefinitionKey key of the process definition
   * @return an optional with the cached process entity
   * @throws {@link CacheLoaderFailedException}
   */
  Optional<CachedProcessEntity> get(long processDefinitionKey);

  /**
   * Put a processEntity entity into the cache.
   *
   * @param processDefinitionKey key of the process definition
   * @param processEntity the process entity to cache
   */
  void put(long processDefinitionKey, CachedProcessEntity processEntity);

  /**
   * Delete a process entity from the cache.
   *
   * @param processDefinitionKey the key of the process definition to delete
   */
  void remove(long processDefinitionKey);

  /** Clear the cache. */
  void clear();

  class CacheLoaderFailedException extends RuntimeException {
    public CacheLoaderFailedException(final Throwable cause) {
      super(cause);
    }
  }
}
