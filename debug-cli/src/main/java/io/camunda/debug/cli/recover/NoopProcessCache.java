/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import java.util.Map;
import java.util.Optional;

/**
 * Throwaway {@link ExporterEntityCache} for driving {@code ProcessHandler} during recovery. {@code
 * ProcessHandler#updateEntity} only ever writes to the cache (to speed up sibling handlers during
 * normal export); it never reads from it, and no sibling handlers run here, so every read returns
 * empty and every write is discarded.
 */
final class NoopProcessCache implements ExporterEntityCache<Long, CachedProcessEntity> {

  @Override
  public Optional<CachedProcessEntity> get(final Long entityKey) {
    return Optional.empty();
  }

  @Override
  public Map<Long, CachedProcessEntity> getAll(final Iterable<Long> keys) {
    return Map.of();
  }

  @Override
  public void put(final Long entityKey, final CachedProcessEntity entity) {
    // discarded: see class javadoc
  }

  @Override
  public void remove(final Long entityKey) {
    // discarded: see class javadoc
  }

  @Override
  public void clear() {
    // nothing cached
  }
}
