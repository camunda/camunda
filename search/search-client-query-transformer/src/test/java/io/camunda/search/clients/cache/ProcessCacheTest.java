/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessCacheTest {

  private ProcessCache processCache;
  private ProcessCache.Configuration configuration;
  private SearchClientBasedQueryExecutor searchExecutor;

  private ProcessDefinitionEntity entity;

  @BeforeEach
  void setUp() {
    entity = new ProcessDefinitionEntity(1L, "name1", "d1", null, null, 1, null, "tenant1", null);

    configuration = ProcessCache.Configuration.getDefault();
    searchExecutor = mock(SearchClientBasedQueryExecutor.class);

    processCache = new ProcessCache(configuration, searchExecutor);

    when(searchExecutor.search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled())))
        .thenReturn(SearchQueryResult.of(entity));
  }

  @AfterEach
  void tearDown() {
    getCache().invalidateAll();
    getCache().cleanUp();
  }

  private Cache<Long, ProcessCacheItem> getCache() {
    return processCache.getRawCache();
  }

  private ConcurrentMap<Long, ProcessCacheItem> getCacheMap() {
    return getCache().asMap();
  }

  @Test
  void shouldPopulateCache() {
    // when
    final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey());

    // then
    verify(searchExecutor, times(1))
        .search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled()));
    assertThat(cacheItem).isNotEqualTo(ProcessCacheItem.EMPTY);
    assertThat(getCacheMap()).containsKey(entity.processDefinitionKey());
  }

  @Test
  void shouldNotPopulateCacheWhenNotFound() {
    // given
    when(searchExecutor.search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled())))
        .thenReturn(SearchQueryResult.of());

    // when
    final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey());

    // then
    verify(searchExecutor, times(1))
        .search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled()));
    assertThat(cacheItem).isEqualTo(ProcessCacheItem.EMPTY);

    assertThat(getCacheMap()).doesNotContainKey(entity.processDefinitionKey());
  }

  @Test
  void shouldEventuallyPopulateAfterInitiallyMissing() {
    // given
    final long missingKey = 42L;

    when(searchExecutor.search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled())))
        .thenReturn(SearchQueryResult.of())
        .thenReturn(
            SearchQueryResult.of(
                new ProcessDefinitionEntity(
                    missingKey, "later", "laterId", null, null, 1, null, "tenant", null)));

    // when - first attempt: missing in secondary storage
    final var first = processCache.getCacheItem(missingKey);

    // then
    assertThat(first).isEqualTo(ProcessCacheItem.EMPTY);
    assertThat(getCacheMap()).doesNotContainKey(missingKey);

    // when - second attempt: appears in secondary storage
    final var second = processCache.getCacheItem(missingKey);

    // then
    assertThat(second).isNotEqualTo(ProcessCacheItem.EMPTY);
    assertThat(second.processName()).isEqualTo("later");

    verify(searchExecutor, times(2))
        .search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled()));
  }

  @Test
  void shouldRepopulateAndRemoveLeastRecentlyUsed() {
    // given
    configuration = new ProcessCache.Configuration(2, 100L);
    processCache = new ProcessCache(configuration, searchExecutor);

    when(searchExecutor.search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled())))
        .thenAnswer(
            invocation -> {
              final var query = invocation.getArgument(0, ProcessDefinitionQuery.class);

              // This test calls getCacheItem(k) which builds a query containing exactly that key.
              final List<Long> keys = query.filter().processDefinitionKeys();
              if (keys.isEmpty()) {
                return SearchQueryResult.of();
              }
              final int key = Math.toIntExact(keys.getFirst());

              return switch (key) {
                case 1 ->
                    SearchQueryResult.of(
                        new ProcessDefinitionEntity(
                            1L, "n1", "d1", null, null, 1, null, "t", null));
                case 2 ->
                    SearchQueryResult.of(
                        new ProcessDefinitionEntity(
                            2L, "n2", "d2", null, null, 1, null, "t", null));
                case 3 ->
                    SearchQueryResult.of(
                        new ProcessDefinitionEntity(
                            3L, "n3", "d3", null, null, 1, null, "t", null));
                default -> SearchQueryResult.of();
              };
            });

    processCache.getCacheItem(1L);
    processCache.getCacheItem(2L);
    getCache().cleanUp();
    assertThat(getCacheMap()).hasSize(2);

    // when - read 1 and adding 3
    processCache.getCacheItem(1L);
    processCache.getCacheItem(3L);
    getCache().cleanUp();

    // then - 2 should be removed (LRU)
    assertThat(getCacheMap().keySet()).containsExactlyInAnyOrder(1L, 3L);
  }

  @Test
  void shouldNotPersistEmptyResultsForBulkLoad() {
    // given
    final long presentKey = 1L;
    final long missingKey = 2L;

    when(searchExecutor.search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled())))
        .thenReturn(
            SearchQueryResult.of(
                new ProcessDefinitionEntity(
                    presentKey, "name1", "d1", null, null, 1, null, "tenant", null)))
        .thenReturn(
            SearchQueryResult.of(
                new ProcessDefinitionEntity(
                    presentKey, "name1", "d1", null, null, 1, null, "tenant", null),
                new ProcessDefinitionEntity(
                    missingKey, "name2", "d2", null, null, 1, null, "tenant", null)));

    // when - first bulk request: missingKey not yet visible
    final var first = processCache.getCacheItems(Set.of(presentKey, missingKey));

    // then
    assertThat(first.getProcessItem(presentKey)).isNotEqualTo(ProcessCacheItem.EMPTY);
    assertThat(first.getProcessItem(missingKey)).isEqualTo(ProcessCacheItem.EMPTY);

    // Only presentKey should be cached (no negative caching)
    assertThat(getCacheMap()).containsKey(presentKey);
    assertThat(getCacheMap()).doesNotContainKey(missingKey);

    // when - second bulk request: missingKey now visible
    final var second = processCache.getCacheItems(Set.of(presentKey, missingKey));

    // then
    assertThat(second.getProcessItem(missingKey)).isNotEqualTo(ProcessCacheItem.EMPTY);
    assertThat(getCacheMap()).containsKey(missingKey);

    verify(searchExecutor, times(2))
        .search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled()));
  }
}
