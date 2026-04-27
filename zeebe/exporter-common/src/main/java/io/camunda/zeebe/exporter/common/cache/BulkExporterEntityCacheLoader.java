/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import java.util.Map;
import java.util.Set;

/** Explicit marker for cache loaders that support bulk loading via {@link #loadAll(Set)}. */
public interface BulkExporterEntityCacheLoader<K, T> extends CacheLoader<K, T> {

  @Override
  Map<? extends K, ? extends T> loadAll(Set<? extends K> keys) throws Exception;
}
