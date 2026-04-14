/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RdbmsEntityCacheLoader<K, Entity, CachedEntity, Query>
    implements CacheLoader<K, CachedEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsEntityCacheLoader.class);

  private final String entityName;
  private final Function<K, Optional<Entity>> findOne;
  private final Function<Set<? extends K>, Query> bulkQueryFactory;
  private final Function<Query, Iterable<Entity>> search;
  private final Function<Entity, K> keyExtractor;
  private final Function<Entity, CachedEntity> mapper;

  public RdbmsEntityCacheLoader(
      final String entityName,
      final Function<K, Optional<Entity>> findOne,
      final Function<Entity, K> keyExtractor,
      final Function<Entity, CachedEntity> mapper) {
    this(entityName, findOne, null, null, keyExtractor, mapper);
  }

  public RdbmsEntityCacheLoader(
      final String entityName,
      final Function<K, Optional<Entity>> findOne,
      final Function<Set<? extends K>, Query> bulkQueryFactory,
      final Function<Query, Iterable<Entity>> search,
      final Function<Entity, K> keyExtractor,
      final Function<Entity, CachedEntity> mapper) {
    this.entityName = entityName;
    this.findOne = findOne;
    this.bulkQueryFactory = bulkQueryFactory;
    this.search = search;
    this.keyExtractor = keyExtractor;
    this.mapper = mapper;
  }

  @Override
  public CachedEntity load(final @NotNull K key) throws Exception {
    return findOne
        .apply(key)
        .map(mapper)
        .orElseGet(
            () -> {
              LOG.debug("{} '{}' not found in RDBMS", entityName, key);
              return null;
            });
  }

  @Override
  public @NotNull Map<K, CachedEntity> loadAll(final @NotNull Set<? extends K> keys)
      throws Exception {
    if (bulkQueryFactory == null || search == null) {
      final Map<K, CachedEntity> entities = new HashMap<>();
      for (final K key : keys) {
        final var entity = load(key);
        if (entity != null) {
          entities.put(key, entity);
        }
      }
      return entities;
    }

    final var query = bulkQueryFactory.apply(keys);
    return toCachedEntities(search.apply(query));
  }

  private Map<K, CachedEntity> toCachedEntities(final Iterable<Entity> entities) {
    return java.util.stream.StreamSupport.stream(entities.spliterator(), false)
        .collect(Collectors.toMap(keyExtractor, mapper));
  }
}
