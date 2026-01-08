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

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.NoSuchElementException;
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
    getCache().cleanUp();
    getCache().invalidateAll();
    getCache().cleanUp();
  }

  private LoadingCache<Long, ProcessCacheItem> getCache() {
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
        .thenThrow(new NoSuchElementException());

    // when
    final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey());

    // then
    verify(searchExecutor, times(1))
        .search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled()));
    assertThat(cacheItem).isEqualTo(ProcessCacheItem.EMPTY);
  }

  @Test
  void shouldNotRepopulateWhenAlreadyCached() {
    // when
    processCache.getCacheItem(entity.processDefinitionKey());
    getCache().cleanUp();
    processCache.getCacheItem(entity.processDefinitionKey());

    // then
    verify(searchExecutor, times(1))
        .search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled()));
  }

  @Test
  void shouldPopulateCacheWithMultiple() {
    // given
    final var otherEntity =
        new ProcessDefinitionEntity(2L, "name2", "d2", null, null, 1, null, "tenant2", null);

    when(searchExecutor.search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled())))
        .thenReturn(SearchQueryResult.of(entity, otherEntity));

    // when
    final var cacheResult =
        processCache.getCacheItems(
            Set.of(entity.processDefinitionKey(), otherEntity.processDefinitionKey()));

    // then
    verify(searchExecutor, times(1))
        .search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled()));

    assertThat(cacheResult.getProcessItem(entity.processDefinitionKey()))
        .isNotEqualTo(ProcessCacheItem.EMPTY);
    assertThat(cacheResult.getProcessItem(otherEntity.processDefinitionKey()))
        .isNotEqualTo(ProcessCacheItem.EMPTY);

    assertThat(getCacheMap())
        .containsOnlyKeys(entity.processDefinitionKey(), otherEntity.processDefinitionKey());
  }

  @Test
  void shouldRepopulateAndRemoveLeastRecentlyUsed() {
    // given
    configuration = new ProcessCache.Configuration(2, 100L);
    processCache = new ProcessCache(configuration, searchExecutor);

    when(searchExecutor.search(any(), eq(ProcessEntity.class), eq(ResourceAccessChecks.disabled())))
        .thenAnswer(
            invocation -> {
              // Return a single ProcessDefinitionEntity matching the requested key if possible.
              // The provider queries in bulk for LRU test, so just return 1 item with the first key
              // and let the cache fill others as EMPTY.
              return SearchQueryResult.of(
                  new ProcessDefinitionEntity(1L, "n", "d", null, null, 1, null, "t", null));
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
}
