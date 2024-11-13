/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache;

import java.util.Optional;

public interface ExporterEntityCache<K, T> {

  /**
   * Get cached entity for the given entityKey. If the entity is not cached, it will be loaded from
   * the configured backed. If no entity is found then the returned optional will be empty. If the
   * query to backend fails otherwise, the method will throw an exception.
   *
   * @param entityKey key of the entity
   * @return an optional with the cached entity
   * @throws {@link CacheLoaderFailedException}
   */
  Optional<T> get(K entityKey);

  /**
   * Put entity into the cache.
   *
   * @param entityKey key of the entity
   * @param entity the entity to cache
   */
  void put(K entityKey, T entity);

  /**
   * Delete an entity from the cache.
   *
   * @param entityKey the key of the entity to delete
   */
  void remove(K entityKey);

  /** Clear the cache. */
  void clear();

  class CacheLoaderFailedException extends RuntimeException {
    public CacheLoaderFailedException(final Throwable cause) {
      super(cause);
    }
  }
}
