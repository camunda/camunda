/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.query.MappingQuery;
import io.camunda.security.auth.SecurityContext;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MappingsCache implements CacheLoader<String, MappingEntity> {

  private final SearchClientBasedQueryExecutor searchExecutor;
  private final LoadingCache<String, MappingEntity> cache;
  private final Duration mappingsCacheRefreshInterval;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public MappingsCache(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final Duration mappingsCacheRefreshInterval) {
    this.mappingsCacheRefreshInterval = mappingsCacheRefreshInterval;
    searchExecutor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            DocumentAuthorizationQueryStrategy.NONE,
            SecurityContext.withoutAuthentication());
    cache = Caffeine.newBuilder().recordStats().build(this::load);
    preloader();
  }

  @Override
  public MappingEntity load(final String key) throws Exception {
    return searchExecutor
        .search(
            MappingQuery.of(q -> q.filter(f -> f.mappingId(key))),
            io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class)
        .items()
        .stream()
        .findFirst()
        .map(MappingEntity.class::cast)
        .orElseThrow();
  }

  public MappingEntity get(final String mappingId) {
    return cache.get(mappingId);
  }

  public Collection<MappingEntity> getAll(final Iterable<String> mappingIds) {
    return cache.getAll(mappingIds).values();
  }

  public Collection<MappingEntity> getAll() {
    return cache.asMap().values();
  }

  private void preloader() {
    scheduler.scheduleAtFixedRate(
        () -> {
          final var res =
              searchExecutor.findAll(
                  MappingQuery.of(q -> q),
                  io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class);
          if (!res.isEmpty()) {
            cache.invalidateAll();
            res.stream()
                .map(MappingEntity.class::cast)
                .forEach(mapping -> cache.put(mapping.mappingId(), mapping));
          }
        },
        0,
        mappingsCacheRefreshInterval.getSeconds(),
        TimeUnit.SECONDS);
  }
}
